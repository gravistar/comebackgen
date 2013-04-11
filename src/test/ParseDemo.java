package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;

import util.Crawler;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

class ParserDemo {

	private static final String BANK_PATH = "bank/";
	private static final String DATA_PATH = "data/";
	private static final String DATA_INPUT_PATH = DATA_PATH + "input/";
	private static final String DATA_OUTPUT_PATH = DATA_PATH + "output/";
	private static final String MODELS_PATH = "lib/edu/stanford/nlp/models/lexparser/";
	private static final String ENGLISH_PCFG_PATH = MODELS_PATH + "englishPCFG.ser.gz";

	private static final LexicalizedParser lp = LexicalizedParser.loadModel(ENGLISH_PCFG_PATH); 
	static {
		lp.setOptionFlags(new String[]{"-maxLength", "80", "-retainTmpSubcategories"});
	}
	
	private static final TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	private static final GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	
	
	public interface SentenceFn<T> {
		public T apply(String sentence);
		public T apply(String sentence, PrintWriter out);
	}
	
	
	public static <T>void processFile(File input, SentenceFn<T> callback) throws IOException {
		
		BufferedReader in = new BufferedReader(new FileReader(input));
		StringTokenizer inputTok = new StringTokenizer(input.getPath(), "/");
		String tail = "dumb";
		while(inputTok.hasMoreTokens()) {
			tail = inputTok.nextToken();
		}
		String outputFileName = DATA_OUTPUT_PATH + tail;
		PrintWriter out = new PrintWriter(new FileWriter(outputFileName));
		
		
		String line,tagged;
		while ((line = in.readLine()) != null) {
			parseSentence(line, out, callback);

			// Output the result
			out.write('\n');
		}
		in.close();
	}
	
	
	/**
	 * This makes the 
	 * @param sentence
	 */
	public static <T> T parseSentence(String sentence, PrintWriter out, SentenceFn<T> callback) {
		return callback.apply(sentence, out);
	}
	
	private static Set<String> verbs = Sets.newHashSet(Arrays.asList("VB", "VBG", "VBD"));
	private static Set<String> restricted = Sets.newHashSet(Arrays.asList("You", "you", "Your", "your", "I", "my"));
	private static Map<String, Set<Tree>> substitutionBank; // key: either a tagged value or CFG value
														   // value: a list of trees from the bank who have the key as their root label
	
	private static Map<String, Set<Tree>> buildSubstitutionBank() throws IOException {
		Map<String, Set<Tree>> bank = Maps.newHashMap();
		File bankFile = new File(DATA_PATH + BANK_PATH + "cards");
		BufferedReader in = new BufferedReader(new FileReader(bankFile));
		String line;
		while ((line = in.readLine()) != null){
			Tree parse = lp.apply(line);	
			Map<String, Set<Tree>> toAdd = Maps.newHashMap();
		
			// Take out root
			if (parse.value().equals("ROOT") || parse.value().equals("FRAG"))
				parse = parse.firstChild();
			
			// Take out S
			if (parse.value().equals("S")) {
				for (Tree child : parse.children()){
					Set<Tree> toPut = Sets.newHashSet();
					if (toAdd.keySet().contains(child.value()))
						toPut.addAll(toAdd.get(child.value()));
					toPut.add(child);
					toAdd.put(child.value(), toPut);
				}
			} else {
				Set<Tree> toPut = Sets.newHashSet();
				if (toAdd.keySet().contains(parse.value()))
					toPut.addAll(toAdd.get(parse.value()));
				toPut.add(parse);
				toAdd.put(parse.value(), toPut);
			}
			
			for (String key : toAdd.keySet()) {
				Set<Tree> toPut = Sets.newHashSet();
				if (bank.keySet().contains(key))
					toPut.addAll(bank.get(key));
				toPut.addAll(toAdd.get(key));
				bank.put(key, toPut);
			}
			int k = 1;
		}
		return bank;
	}
	
	/**
	 * Get all the possible replacements for a given set of parse trees given the
	 * substitution bank.
	 * @return
	 */
	private static Set<Tree> generateAllReplacements(List<Tree> roots, Set<Tree> substitutions) {
		Set<Tree> generatedTrees = Sets.newHashSet();
		for (Tree root : roots) {
			for (Tree substitution : substitutions){
				generatedTrees.addAll(plugInReplacements(root, substitution));
			}
		}
		return generatedTrees;
	}
	
	
	/**
	 * Generates all trees where the replacement is "plugged in" as a subtree
	 * to the root.  By necessity, deep copies are made of the root.  Otherwise, 
	 * the traversal doesn't make any sense.
	 * 
	 * @param root
	 * @param replacement
	 * @return
	 */
	private static Set<Tree> plugInReplacements(Tree root, Tree replacement) {
		Set<Tree> replacements = Sets.newHashSet();
		
		List<Tree> nodes = root.preOrderNodeList();
		
		int index = 0;
		for (Tree node : nodes) {
			if (isValidReplacement(node, replacement)) {
				Tree deepcopy = root.deepCopy();
				List<Tree> copiedNodes = deepcopy.preOrderNodeList();
				copiedNodes.set(index, replacement);
				replacements.add(deepcopy);
			}
			index++;
		}
		
		return replacements;
	}
	
	/**
	 * Right now this just uses the following dumb rule:
	 * <ul>
	 * <li>The replacement tree must have the same structure as the tree being replaced.
	 * <li>If two nodes in the same position are not leaves, they must have the same value UNLESS
	 * 		they are verbs. If they are both verbs, then they just need to both be verbs.
	 * 	   It's very questionable if this is too restrictive.
	 * <li>If two nodes in the same position are leaves, they must have the same value if
	 * 	   they both have a restricted value.
	 * </ul> 
	 * @param toReplace
	 * @param replacement
	 * @return
	 */
	private static boolean isValidReplacement(Tree toReplace, Tree replacement) {
		List<Tree> toReplacePreOrder = toReplace.preOrderNodeList();
		List<Tree> replacementPreOrder = toReplace.preOrderNodeList();
		
		if (toReplacePreOrder.size() != replacementPreOrder.size()) return false;
		
		for (int i=0; i<toReplacePreOrder.size(); i++) {
			Tree t1 = toReplacePreOrder.get(i);
			Tree t2 = replacementPreOrder.get(i);
			
			String value1 = t1.label().value();
			String value2 = t2.label().value();
			
			// if phrasal fields dont match, bail
			if (t1.isPhrasal() != t2.isPhrasal())
				return false;
			
			// if leaf fields don't match, bail
			if (t1.isLeaf() != t2.isLeaf())
				return false;
			
			// if preterminal fields don't match, bail
			if (t1.isPreTerminal() != t2.isPreTerminal())
				return false;
			
			// if this isn't a leaf, the values must be the same UNLESS they are both verbs.
			// then they must both be verbs.
			// this is a questionable restriction, but let's enforce it for now
			if (!t1.isLeaf()) {
				if (verbs.contains(value1) ^ verbs.contains(value2))
					return false;
				
				if (!verbs.contains(value1) && value1 != value2)
					return false;
			}
			
			if (t1.isLeaf()) {
				if (restricted.contains(value1)){
					if (value1 != value2)
						return false;
				}
			}
		}
		
		return true;
	}
	
	public interface TreeFn<T> {
		public T apply(Tree node);
	}
	
	/**
	 * Applies the specified function on the tree in BFS order
	 * @param root
	 * @param applyFn
	 */
	private static <T> void applyOnTree(Tree root, TreeFn<T> applyFn) {
		Set<Tree> seen = Sets.newHashSet();
    	Queue<Tree> q = new LinkedList<Tree>();
    	q.add(root);
		while (!q.isEmpty()) {
			Tree curNode = q.remove();
			applyFn.apply(curNode);
			for (Tree child : curNode.getChildrenAsList()){
				if (!seen.contains(child)){
					q.add(child);
					seen.add(child);
				}
			}
		}
	}
	
	
	private static void exploreTree(Tree root, PrintWriter out) {
		out.write("STARTING TREE EXPLORATION\n");
		Set<Tree> seen = Sets.newHashSet();
    	Queue<Tree> q = new LinkedList<Tree>();
    	q.add(root);
    	int i = 0;
		while (!q.isEmpty()) {
			Tree curNode = q.remove();
			out.write("Node " + Integer.toString(i) + " has label " + curNode.label().toString() + " Phrasal? " + curNode.isPhrasal() + " Terminal? " + curNode.isLeaf() + "\n");
			i++;
			for (Tree child : curNode.getChildrenAsList()){
				if (!seen.contains(child)){
					q.add(child);
					seen.add(child);
				}
			}
		}
		out.write("DONE WITH TREE EXPLORATION\n");
	}
	
	public static void main(String[] args) {
		File startingDir = new File(DATA_INPUT_PATH);
		List<File> files = Crawler.getFilesInDir(startingDir);
		
		// try building bank
		try {
			substitutionBank = buildSubstitutionBank();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		for (File file : files) {
			try {
				processFile(file, new SentenceFn<Void>() {

					@Override
					public Void apply(String sentence) {
						return null;
					}

					@Override
					public Void apply(String sentence, PrintWriter out) {
						// run this on the string itself!! this is better anyway
						Tree parse = lp.apply(sentence);
						
						List<Tree> roots = Lists.newArrayList(parse);
						
						for (String key : substitutionBank.keySet()) {
							Set<Tree> substitutions = substitutionBank.get(key);
							Set<Tree> comebacks = generateAllReplacements(roots, substitutions);
							for (Tree comeback : comebacks)
								out.write(comeback.yieldWords().toString() + "\n");
						}
						
//						exploreTree(parse, out);
//						
//						parse.pennPrint(out);
//						out.write("\n");
//						
//						GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
//						List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
//						out.write(tdl.toString());
//						
//						TreePrint tp = new TreePrint("penn,typedDependenciesCollapsed");
//						tp.printTree(parse, out);
//						return parse;
						
						return null;
					}
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//LexicalizedParser lp = LexicalizedParser.loadModel(ENGLISH_PCFG_PATH); //<--TODO path to grammar goes here
		
	}

}