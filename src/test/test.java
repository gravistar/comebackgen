package test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class test {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 */
//	public static void main(String[] args) throws ClassNotFoundException, IOException {
//		// Initialize the tagger
//		 
//		MaxentTagger tagger = new MaxentTagger("taggers/wsj-0-18-bidirectional-distsim.tagger");
//		
//		// The sample string
//		BufferedReader in = new BufferedReader(new FileReader("data/yomama.in"));
//		BufferedWriter out = new BufferedWriter(new FileWriter("data/yomama.out"));
//		
//		
//		String line,tagged;
//		while ((line = in.readLine()) != null) {
//			// The tagged string
//			tagged = tagger.tagString(line);
//
//			// Output the result
//			out.write(tagged);
//			out.write('\n');
//		}
//		in.close();
//		out.close();
//	}

}
