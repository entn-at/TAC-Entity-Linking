package tac.kbp.entitylinking.ranking;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aliasi.stats.Statistics;

import tac.kbp.configuration.Definitions;
import tac.kbp.entitylinking.queries.ELQuery;

public class NILDetector {
	
	public void train(List<ELQuery> queries, String outputfile) throws IOException, InterruptedException {
		
		//extract features
		FileWriter fstream = new FileWriter(outputfile);
		BufferedWriter out = new BufferedWriter(fstream);
		
		for (ELQuery q : queries) {

			double[] features = extract_features(q);
			out.write(Double.toString(features[0]));
				
			for (int i = 1; i < features.length; i++) {
				out.write(' '+ Integer.toString(i) + ':' + features[i]);
			}
			
			out.write(" # " + q.query_id + ' ' + q.gold_answer + '\n');
		}
		
		out.close();
		
		//train classifier
		Runtime runtime = Runtime.getRuntime();
		String learn_arguments = "NIL_train.dat NIL_detector.dat";
		System.out.println("Training SVMLight model (NIL Detector): ");
		System.out.println(Definitions.SVMLightPath+Definitions.SVMLightLearn+' '+learn_arguments);
		Process svmLearn = runtime.exec(Definitions.SVMLightPath+Definitions.SVMLightLearn+' '+learn_arguments);
		svmLearn.waitFor();
	}
	
	double[] extract_features(ELQuery q) {
		
		/* based on score 
		- ranking score
		- mean score
		- difference from mean score
		- standard deviation
		- Dixon's Q Test for Outliers
		- Grubb's Test for Outliers
		*/
		
		/* based on atomic features 
		- kldivergence
		- topic_cosine_similarity
		- average string similarities
		*/
		
		double[] features = new double[6];
		
		//System.out.println("q.query_id: " + q.name);		
		//System.out.println("number of candiadtes: " + q.candidatesRanked.size());
		
		if (q.candidatesRanked.size()>1) {
		
			double[] scores = new double[q.candidatesRanked.size()];
			
			/*
			double[] kldivergence = new double[q.candidatesRanked.size()];
			double[] textual_cosine_similarity = new double[q.candidatesRanked.size()];
			double[] average_string_similarities = new double[q.candidatesRanked.size()];
			*/
			
			for (int i = 0; i < q.candidatesRanked.size(); i++) {
				scores[i] = q.candidatesRanked.get(i).conditionalProbabilities[1];
				
				//candidatesRanked contains only EID and score, features must be extracted from q.candidates HashSet			
				//kldivergence[i] = q.getCandidate(q.candidatesRanked.get(i).entity.id).features.kldivergence; 
				//textual_cosine_similarity[i] = q.getCandidate(q.candidatesRanked.get(i).entity.id).features.cosine_similarity;
				//average_string_similarities[i] = q.getCandidate(q.candidatesRanked.get(i).entity.id).features.average_similarities;			
			}
			
			/* ranking scores */
			double mean_scores = Statistics.mean(scores);
			double std_dvt_scores = Statistics.standardDeviation(scores);
			double diff_mean_scores = Math.abs(q.candidatesRanked.get(0).conditionalProbabilities[1]-mean_scores);
			double dixonTest_scores = (scores[0]-scores[1]) / (scores[0]-scores[scores.length-1]); 
			double grubbsTest_scores = (scores[0]-mean_scores) / std_dvt_scores;
			
			features[1] = mean_scores;
			features[2] = std_dvt_scores;
			features[3] = diff_mean_scores;
			features[4] = dixonTest_scores;
			features[5] = grubbsTest_scores;
		}
		
		else if (q.candidatesRanked.size()==1) {
			
			features[1] = 0;
			features[2] = q.candidatesRanked.get(0).conditionalProbabilities[1];
			features[3] = 0;
			features[4] = 0;
			features[5] = 0;
			
		}
		
		else {
			
			features[1] = 0;
			features[2] = 0;
			features[3] = 0;
			features[4] = 0;
			features[5] = 0;
		}
		
		if (q.gold_answer.startsWith("NIL"))
			features[0] = 1;
		else 
			features[0] = -1;

		return features;
		
		/*
		// kl-divergence
		double mean_kldivergence = Statistics.mean(kldivergence);
		double std_dvt_kldivergence = Statistics.standardDeviation(kldivergence);
		double diff_mean_kldivergence = Math.abs(q.candidatesRanked.get(0).features.kldivergence-mean_kldivergence);
		
		// cosine sim
		double mean_textual_cosine_similarity = Statistics.mean(textual_cosine_similarity);
		double std_dvt_textual_cosine_similarity = Statistics.standardDeviation(textual_cosine_similarity);
		double diff_mean_textual_cosine_similarity = Math.abs(q.candidatesRanked.get(0).features.cosine_similarity-mean_textual_cosine_similarity);		
		
		// avg string sim
		double mean_average_string_similarities = Statistics.mean(average_string_similarities);		
		double std_average_string_similarities = Statistics.standardDeviation(average_string_similarities);
		double diff_mean_string_similarities = Math.abs(q.candidatesRanked.get(0).features.average_similarities-mean_average_string_similarities);
		*/
	}
	
	public void classify(List<ELQuery> queries, String outputfile) throws IOException, InterruptedException{
		
		//extract features from top candidate
		FileWriter fstream = new FileWriter(outputfile);
		BufferedWriter out = new BufferedWriter(fstream);
		
		for (ELQuery q : queries) {
			
			double[] features = extract_features(q);
			out.write(Double.toString(features[0]));
			
			for (int i = 1; i < features.length; i++) {
				out.write(' '+ Integer.toString(i) + ':' + features[i]);
			}
			
			if (q.candidatesRanked.size()>0)
				out.write(" #" + q.query_id + ' ' + q.candidatesRanked.get(0).entity.id + '\n');
			else out.write(" #" + q.query_id + ' ' + "no candidates" + '\n');
				
		}
		
		out.close();
		
		//classify
		Runtime runtime = Runtime.getRuntime();
		String classify_arguments = " NIL_test.dat NIL_detector.dat NIL_predictions";
		System.out.println("Testing SVMLight model (NIL Detector): ");
		System.out.println(Definitions.SVMLightPath+Definitions.SVMLightClassify+' '+classify_arguments);
		Process SVMLightClassify = runtime.exec(Definitions.SVMLightPath+Definitions.SVMLightClassify+' '+classify_arguments);
		SVMLightClassify.waitFor();
	}
	
	public void clusterNILs(Set<String> queries_NIL, Map<String,String> queries_answers) throws FileNotFoundException{
		
		Map<String,Set<String>> cluster = new HashMap<String, Set<String>>();
		
		int count = 0;
		
		for (ELQuery q1 : Definitions.queriesTest) {
			
			// check if its a query whose answer was classified as NIL
			if (queries_NIL.contains(q1.query_id)) {
				
				String id = "NIL"+String.valueOf(count);
				Set<String> queries = new HashSet<String>();
				
				// gather all other queries that have an equal name-string
				for (ELQuery q2 : Definitions.queriesTest) {

					if (q1.name.equalsIgnoreCase(q2.name)) {
						queries.add(q2.query_id);
					}
				}
				queries.add(q1.query_id);
				cluster.put(id, queries);
				count++;
			}			
		}
	
		Set<String> keys = queries_answers.keySet();
		List<String> sortedKeys = new ArrayList<String>(keys);
		Collections.sort(sortedKeys);
		
		Set<String> clusterKeys = cluster.keySet();		
		List<String> clusterSortedKeys = new ArrayList<String>(clusterKeys);
		Collections.sort(clusterSortedKeys);
		
		/* For debbugging */
		/*
		System.out.println("ID Labels:");
		for (String id_label : clusterSortedKeys) {
			System.out.println(id_label);
			Set<String> queries = cluster.get(id_label);
			for (String q : queries) {
				System.out.println('\t'+q);
			}
		}
		*/
		
		String output = ("results-SVMRank-NIL-Detector-NIL-clustering.txt");		
		PrintStream out = new PrintStream( new FileOutputStream(output));		
		
		for (String q : sortedKeys) {			
			
			if (queries_NIL.contains(q)) {
				
				for (String id_label : clusterSortedKeys) {
					Set<String> queries = cluster.get(id_label);
					if (queries.contains(q)) {
						out.println(q+'\t'+id_label);
						break;
					}
				}				
			}
			
			else {
				out.println(q+'\t'+queries_answers.get(q));
			}
		}
		
		out.close();

	}
}