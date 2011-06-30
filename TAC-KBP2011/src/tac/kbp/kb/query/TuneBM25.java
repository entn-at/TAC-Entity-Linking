package tac.kbp.kb.query;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import tac.kbp.kb.query.GoldenStandardQuery;
import tac.kbp.kb.utils.Pair;

public class TuneBM25 {
	
	List<GoldenStandardQuery> queries = null;
	BM25Query bm25query = null;
	Searcher searcher = null;
	HashMap<Pair, BM25TuneResults> results = null;
	
	public TuneBM25(String index, String fields_size, String queries_file) throws NumberFormatException, IOException, ParseException {
		
		queries = new LinkedList<GoldenStandardQuery>();
		loadQueries(queries_file);
		
		System.out.println( queries.size() + " queries loaded");
		
		searcher = new IndexSearcher(FSDirectory.getDirectory(index));
		results = new HashMap<Pair, BM25TuneResults>();
		
		Float divisor = new Float(10);

		//K1
		for (int k1 = 0; k1 <= 20; k1++) {			
			//B
			for (int b = 0; b <= 10; b++) {	
				Pair pair = new Pair(k1 / divisor, b / divisor);
				results.put(pair, new BM25TuneResults());
			}
		}
		
		Set<Pair> keys = results.keySet();
		
		
		
		for (Iterator<Pair> iterator = keys.iterator(); iterator.hasNext();) {
			
			Pair pair = (Pair) iterator.next();
			System.out.print("B="+pair.getB()+"; ");
			System.out.print("K1="+pair.getK1());
			System.out.println();
			bm25query = new BM25Query(searcher, fields_size, pair.getK1(),pair.getB());
			results.put(pair, processQueries());
		
			// Write results to file
			
			FileWriter outFile = new FileWriter("results_"+pair.getK1()+"_"+pair.getB()+".txt");
			PrintWriter out = new PrintWriter(outFile);
			
			out.println("K1: " + pair.getK1());
			out.println("B: " + pair.getB());
			
			BM25TuneResults result =  results.get(pair);
			
			out.println(result.getResults());
			out.close();
			
		}

	}
	
	public BM25TuneResults processQueries() throws IOException, ParseException {
		
		BM25TuneResults results = new BM25TuneResults();
		
		for (GoldenStandardQuery test_query : this.queries) {
			if (!test_query.entity_kb_id.startsWith("NIL")) {
				//System.out.print("querying for: " + test_query.name);
				float score = query(test_query);
				results.setScore(test_query.query_id, score);
				}
			}		
		return results;

	}
	
	public float query(GoldenStandardQuery query) throws IOException, ParseException {
		
		TopDocs top = bm25query.BM25F(query.name);
		
		//System.out.println("totalHits: " + top.totalHits);
		ScoreDoc[] docs = top.scoreDocs;
		//System.out.print("\ttop.scoreDocs.length: "+ top.scoreDocs.length);
		//boolean found = false;
		float score = (float) 0.0;
		
		for (int i = 0; i < top.scoreDocs.length; i++) {			
			
			Document doc = searcher.doc(docs[i].doc);
			String id = doc.getField("id").stringValue();
			
			if (id==query.entity_kb_id) {
				score = docs[i].score;
				//found = true;
				break;
			}
		}
		
		/*
		if (found) {
			System.out.print("\tFound\n");
		}
		else {
			System.out.print("\tNot Found\n");
		}
		*/
		
		return score;
	}

	
	public void loadQueries(String file) {
		
		try {
			
			//Open the file that is the first
			
			System.out.println("opening queries file: " + file);
			
			FileInputStream fstream = new FileInputStream(file);
			
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			String delims = "\\t";
			
			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				String[] tokens = strLine.split(delims);				
				GoldenStandardQuery query = new GoldenStandardQuery(tokens[0], tokens[1], tokens[2], tokens[3], tokens[4]); 
				this.queries.add(query);				
				
			}
			// Close the input stream
			in.close();
			
		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}

}