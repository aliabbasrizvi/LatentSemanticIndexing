package invert;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.TreeMap;

public class InvertedFile {

	String dirPath, stopListPath;
	ArrayList<String> stopWordList = new ArrayList<String>();
	TreeMap<String, Integer> wordList = new TreeMap<String, Integer>();
	TreeMap<String, TreeMap<String, Integer>> wordOccurrence = new TreeMap<String, TreeMap<String, Integer>>();
	TreeMap<String, Integer> documentLength = new TreeMap<String, Integer>();
	int contextWords = 15;
	int totalDocuments = 0;
	public static final String whiteSpacePattern = "\\s";

	public InvertedFile(String directory, String stoplist) {
		dirPath = directory;
		stopListPath = stoplist;
	}

	/**
	 * 
	 * @throws IOException
	 * 
	 * Create a stop list from the stop-list file provided	
	 */
	public void createStopList() throws IOException {
		FileInputStream fstream = null;
		String line;
		try {
			fstream = new FileInputStream(stopListPath);
		} catch (FileNotFoundException e) {
			System.out.println("File does not exist. Set correct path");
		}

		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));

		while((line = br.readLine()) != null) {
			stopWordList.add(line.trim().toLowerCase());
		}
	}

	/**
	 * 
	 * @param word
	 * @return Whether the term is a stop list word or not
	 */
	public boolean isStopListWord(String word) {
		if (stopWordList.contains(word.toLowerCase()))
			return true;

		return false;
	}
	
	/**
	 * 
	 * @param term
	 * @return Whether the term appears in the word list or not
	 */
	public boolean isTermOnWordList(String term) {
		if (wordList.containsKey(term.toLowerCase()))
			return true;

		return false;
	}

	/**
	 * 
	 * @param term
	 * @param document
	 * @return Whether term appears in a particular document or not
	 */
	public boolean isTermInDocument(String term, String document) {
		TreeMap<String, Integer> documentList = wordOccurrence.get(term.toLowerCase());

		if (documentList.containsKey(document))
			return true;

		return false;
	}
	
	/**
	 * 
	 * @return Total number of terms across all documents
	 */
	public int getNumberOfTerms() {
		return wordList.size();
	}
	
	/**
	 * 
	 * @return Total number of documents
	 */
	public int getNumberOfDocuments() {
		return totalDocuments;
	}
	
	/**
	 * 
	 * @return Entire word list
	 */
	public TreeMap<String, Integer> getWordList() {
		return wordList;
	}
	
	/**
	 * 
	 * @return TF.IDF values arranged in a double dimension matrix 
	 */
	public double[][] getTermMatrixValues() {
		double[][] matrixTerms = new double[getNumberOfTerms()][getNumberOfDocuments()];
		int i, j;
		
		i = 0;
		for (String term : wordList.keySet()) {
			j = 0;
			for (String document : documentLength.keySet()) {
				matrixTerms[i][j] = getTFInDocument(term, document) * getIDF(term);
				j++;
			}
			i++;
		}
		
		return matrixTerms;
	}
	
	/**
	 * 
	 * @throws IOException
	 * 
	 * Iterate over all files in a directory which form the document corpus
	 */
	public void iterateOverDirectory() throws IOException {
		File dir = new File(dirPath);
		for (File file : dir.listFiles()) {
			if (file.getName().equals(".") ||
					file.getName().equals("..") || 
					file.isHidden() || 
					file.isDirectory())
				continue;

			parseFile(file.getAbsolutePath(), file.getName());
			totalDocuments++;
		}
	}

	/**
	 * 
	 * @param filePath
	 * @param fileName
	 * @throws IOException
	 * 
	 * Parse a single file having path filePath and name fileName and populate word list and posting data
	 */
	private void parseFile(String filePath, String fileName) throws IOException {
		FileInputStream fstream = null;
		String line, word;
		TreeMap<String, Integer> wordFreq = null;
		int wordCounter = 0, i;

		try {
			fstream = new FileInputStream(filePath);
		} catch (FileNotFoundException e) {
			System.out.println("File does not exist. Set correct path");
		}

		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));

		while((line = br.readLine()) != null) {
			String wordsInLine[] = line.split(whiteSpacePattern);
			for (i = 0; i < wordsInLine.length; i++) {
				word = wordsInLine[i].trim().toLowerCase();
				if (isStopListWord(word)) {
					wordCounter+=1;
					continue;
				}
				if (wordList.containsKey(word)) {
					wordFreq = wordOccurrence.get(word);
					if (wordFreq.containsKey(fileName)) {
						wordFreq.put(fileName, wordFreq.get(fileName) + 1);
					} else {
						wordFreq.put(fileName, 1);
						wordList.put(word, wordList.get(word) + 1);
					}
				} else {
					wordList.put(word, 1);
					wordFreq = new TreeMap<String, Integer>();
					wordFreq.put(fileName, 1);
					wordOccurrence.put(word, wordFreq);
				}
				wordCounter+=1;
			}
		}

		in.close();
		documentLength.put(fileName, wordCounter);
	}

	/**
	 * 
	 * @param term
	 * @return Number of documents a particular term appears in
	 */
	private int getDocumentFrequency(String term) {
		if (wordList.get(term.toLowerCase()) != null)
			return wordList.get(term.toLowerCase());

		return 0;
	}

	/**
	 * 
	 * @param term
	 * @return Inverse document frequency for the term
	 */
	public double getIDF(String term) {
		int termFreq = getDocumentFrequency(term.toLowerCase());
		if (termFreq != 0)
			return (double) (1 + Math.log10(totalDocuments) - Math.log10(termFreq));

		return (Double) null;
	}

	/**
	 * 
	 * @param term
	 * @param document
	 * @return Term frequency for term in document
	 */
	public double getTFInDocument(String term, String document) {
		if (isTermInDocument(term.toLowerCase(), document)) {
			int termAppearance = wordOccurrence.get(term.toLowerCase()).get(document);
			return (1 + Math.log10(termAppearance));
		}

		return 0;
	}

	/**
	 * 
	 * @return List of document names which are there in the corpus
	 */
	public ArrayList<String> getDocumentList() {
		ArrayList<String> documentList = new ArrayList<String>();

		for (String document : documentLength.keySet())
			documentList.add(document);

		return documentList;
	}

	/**
	 * 
	 * @param documentName
	 * @throws IOException
	 * 
	 * Print words from the document <documentName>. 
	 * Total number of words printed equals contextWords
	 */
	public void printWords(String documentName) throws IOException {
		FileInputStream fstream = null;
		int wordCounter = 0;
		String line;
		
		try {
			fstream = new FileInputStream(dirPath + "/" + documentName);
		} catch (FileNotFoundException e) {
			System.out.println("File does not exist. Set correct path");
		}
		
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		while ((line = br.readLine()) != null && wordCounter < contextWords) {
			String [] words = line.split(whiteSpacePattern) ;
			for (int i = 0; i < words.length && wordCounter < contextWords; i++, wordCounter++) {
				System.out.print(words[i] + " ");
			}
		}
		System.out.println("\n");
	}
}
