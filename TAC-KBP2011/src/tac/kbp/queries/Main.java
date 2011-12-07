package tac.kbp.queries;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import tac.kbp.queries.CandidateComparator; 
import tac.kbp.ranking.LogisticRegressionLingPipe;
import tac.kbp.utils.Definitions;
import tac.kbp.utils.lda.SupportDocLDA;

public class Main {
	
	public static void main(String[] args) throws Exception {
		
		if (args.length == 0) {
			usage();
			System.exit(0);
		}
		
		if (args[0].equalsIgnoreCase("train")) {
			
			tac.kbp.utils.Definitions.loadAll(args[1], args[2], args[3]);
			
			System.out.println(tac.kbp.utils.Definitions.queries.size() + " queries loaded");
			System.out.println(tac.kbp.utils.Definitions.stop_words.size() + " stopwords loaded");
			System.out.println(tac.kbp.utils.Definitions.queriesGold.size() + " queries gold standard loaded");
						
			for (Iterator<KBPQuery> iterator = tac.kbp.utils.Definitions.queries.iterator(); iterator.hasNext();) {
				KBPQuery query = (KBPQuery) iterator.next();
				
				System.out.print(query.query_id + " \"" + query.name + '"');
				Train.getSenses(Definitions.binaryjedis, query);
				Train.processQuery(query);		
			}
			
			float miss_rate = (float) Train.MISS_queries / ((float) tac.kbp.utils.Definitions.queries.size());
			float coverage = (float) Train.FOUND_queries / ((float) tac.kbp.utils.Definitions.queries.size());
			
			System.out.println("Documents Retrieved: " + Integer.toString(Train.total_n_docs));
			System.out.println("Queries: " + Integer.toString(tac.kbp.utils.Definitions.queries.size()));
			System.out.println("Docs p/ query: " + ( (float) Train.total_n_docs / (float) tac.kbp.utils.Definitions.queries.size()));
			System.out.println("Queries with 0 docs retrieved: " + Integer.toString(Train.n_queries_zero_docs));
			System.out.println("Queries NIL: " + Train.NIL_queries);
			System.out.println("Queries Not Found (Miss Rate): " + Train.MISS_queries + " (" + miss_rate * 100 + "%)" );
			System.out.println("Queries Found (Coverage): " + Train.FOUND_queries + " (" + coverage * 100 + "%)" );

			//training logistic regression model
			LogisticRegressionLingPipe trainning = new LogisticRegressionLingPipe(Train.inputs, Train.outputs);
			trainning.trainModel();
			
			//save model to disk
			trainning.writeModel("linear-regression");
			
			//close indexes and REDIS connection
			Definitions.searcher.close();
			Definitions.documents.close();
			Definitions.binaryjedis.disconnect();
		}
		
		else if (args[0].equalsIgnoreCase("testVectors")) {
			
			//load queries
			//get candidates
			//extract features
						
			LogisticRegressionLingPipe regression = new LogisticRegressionLingPipe();
			
			//read model from disk
			regression.readModel(args[1]);
			
			//load test features vectors
			regression.loadVectors(args[2]);
			
			//apply the model to loaded feature vectors
			regression.applyTrainedModel();
			
		}
		
		
		else if (args[0].equalsIgnoreCase("test")) {
			
			//load queries
			tac.kbp.utils.Definitions.loadAll(args[2], args[3], args[4]);
			
			System.out.println(tac.kbp.utils.Definitions.queries.size() + " queries loaded");
			System.out.println(tac.kbp.utils.Definitions.queriesGold.size() + " gold standard queries loaded");
			System.out.println(tac.kbp.utils.Definitions.stop_words.size() + " stopwords loaded");			
			
			//get candidates and extract features
			for (Iterator<KBPQuery> iterator = tac.kbp.utils.Definitions.queries.iterator(); iterator.hasNext();) {
				KBPQuery query = (KBPQuery) iterator.next();
				
				System.out.print(query.query_id + " \"" + query.name + '"');
				Train.getSenses(Definitions.binaryjedis, query);
				Train.processQuery(query);		
			}
			
			float miss_rate = (float) Train.MISS_queries / ((float) tac.kbp.utils.Definitions.queries.size());
			float coverage = (float) Train.FOUND_queries / ((float) tac.kbp.utils.Definitions.queries.size());
			
			System.out.println("Documents Retrieved: " + Integer.toString(Train.total_n_docs));
			System.out.println("Queries: " + Integer.toString(tac.kbp.utils.Definitions.queries.size()));
			System.out.println("Docs p/ query: " + ( (float) Train.total_n_docs / (float) tac.kbp.utils.Definitions.queries.size()));
			System.out.println("Queries with 0 docs retrieved: " + Integer.toString(Train.n_queries_zero_docs));
			System.out.println("Queries NIL: " + Train.NIL_queries);
			System.out.println("Queries Not Found (Miss Rate): " + Train.MISS_queries + " (" + miss_rate * 100 + "%)" );
			System.out.println("Queries Found (Coverage): " + Train.FOUND_queries + " (" + coverage * 100 + "%)" );

			//read model from disk
			LogisticRegressionLingPipe regression = new LogisticRegressionLingPipe(Train.inputs, Train.outputs);
			regression.readModel(args[1]);
			
			
			//file to write output results
			FileWriter fstream = new FileWriter("results.txt");
			BufferedWriter out = new BufferedWriter(fstream);
			
			//apply the model to feature vectors
			for (KBPQuery query : Definitions.queries) {
				for (Candidate c: query.candidates) {
					regression.applyTrainedModelCandidate(c);
				}
				
				//rank candidates according to conditional_probabilities
				query.candidatesRanked = new ArrayList<Candidate>(query.candidates);
				Collections.sort(query.candidatesRanked, new CandidateComparator());
				
				//write results to file			
				out.write(query.query_id+'\t'+query.candidatesRanked.get(0).entity.id +"\n");
			}
			out.close();
		}
		
		else if (args[0].equalsIgnoreCase("ldatopics"))
			SupportDocLDA.process(args[1],args[2],args[3],args[4]);
		
		else usage();
	}
	
	public static void usage(){
		System.out.println("Usage:");
		System.out.println("  train queriesPath goldStandardPath queries_lda_topics");
		System.out.println("  test model queriesPath goldStandardPath queries_lda_topics outputFile"); 
		System.out.println("  testVectors model vectorsPath");
		System.out.println("  ldatopics queriesPath stopwords dcIndex outputfile");
		System.out.println();
	}
}