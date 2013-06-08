package lsi;

import invert.InvertedFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import Jama.Matrix;
import Jama.SingularValueDecomposition;

public class LSI {
	InvertedFile invObj;
	SingularValueDecomposition svd;
	Matrix TD, leftSingularMatrix, rightSingularMatrix, singularValueMatrix;
	TreeMap<String, Double> similarityValues;
	boolean eligibleQuery = false;
	int relevantDocs = 5;
	int k;
	
	public LSI(String dirPath, String stoplistPath, int kValue) throws IOException {
		invObj = new InvertedFile(dirPath, stoplistPath);
		invObj.createStopList();
		invObj.iterateOverDirectory();
		k = kValue;
	}
	
	public LSI(String dirPath, String stoplistPath) throws IOException {
		invObj = new InvertedFile(dirPath, stoplistPath);
		invObj.createStopList();
		invObj.iterateOverDirectory();
		k = -1;
	}
		
	/**
	 * Prepares term document matrix by passing double dimension array
	 * consisting of TF.IDF values to the Matrix object.
	 */
	public void createTermDocumentMatrix() {
		TD = new Matrix(invObj.getTermMatrixValues());	
	}
	
	/**
	 * Set matrices as per value of k
	 */
	private void prepareMatrices() {
		for (int i = 0; i < leftSingularMatrix.getRowDimension(); i++) 
			for (int j = k; j < leftSingularMatrix.getColumnDimension(); j++)
				leftSingularMatrix.set(i, j, 0);
		
		for (int i = k; i < singularValueMatrix.getRowDimension(); i++) 
			for (int j = k; j < singularValueMatrix.getColumnDimension(); j++)
				singularValueMatrix.set(i, j, 0);
		
		for (int i = 0; i < rightSingularMatrix.getRowDimension(); i++)
			for (int j = k; j < rightSingularMatrix.getColumnDimension(); j++)
				rightSingularMatrix.set(i, j, 0);
	}
	
	/**
	 * Prepares a object of class SingularValueDecomposition by passing the
	 * term document matrix TD.
	 */
	private void performSingularValueDecomposition() {
		svd = new SingularValueDecomposition(TD);		
		leftSingularMatrix = svd.getU();
		singularValueMatrix = svd.getS();
		rightSingularMatrix = svd.getV();
		
		if (k == -1) {
			k = leftSingularMatrix.getColumnDimension();
		}
		
		if (k >= 0 && k < leftSingularMatrix.getColumnDimension()) {
			prepareMatrices();
		}
	}
	
	/**
	 * 
	 * @param query entered by the user 
	 * @return Query vector represented in terms of a column matrix
	 */
	private Matrix createQueryVector(String query) {
		List<String> words = Arrays.asList(query.split(invert.InvertedFile.whiteSpacePattern));
		TreeMap<String, Integer> wordList = invObj.getWordList();
		Matrix queryVector = new Matrix(wordList.size(), 1);
		int termCounter;
				
		for (termCounter = 0; termCounter < words.size(); termCounter++) 
			words.set(termCounter, words.get(termCounter).toLowerCase());
		
			
		termCounter = 0;
		for (String term : wordList.keySet()) {
			if (words.contains(term)) {
				queryVector.set(termCounter, 0, queryVector.get(termCounter, 0) + 1);
				eligibleQuery = true;
			}
			termCounter++;
		}

		return queryVector;
	}
	
	/**
	 * 
	 * @param matrix (column matrix) for which the vector modulus is to be calculated
	 * @return Vector modulus
	 */
	public double getVectorModulus(Matrix matrix) {
		double product = 0;
		
		for (int i = 0; i < matrix.getRowDimension(); i++)
			product += matrix.get(i, 0) * matrix.get(i, 0);
		
		product = Math.sqrt(product);
		return product;
	}
	
	/**
	 * 
	 * @param queryMatrix
	 * @return Vector modulus for the matrix after multiplying it by the left singular matrix
	 */
	private double getModulus(Matrix matrix) {
		return (getVectorModulus(leftSingularMatrix.transpose().times(matrix)));
	}
	
	/**
	 * 
	 * @param columnNumber
	 * @return Column related to the document from the right singular matrix 
	 */
	private Matrix getDocumentColumnMatrix(int columnNumber) {
		Matrix columnMatrix = new Matrix(rightSingularMatrix.getColumnDimension(), 1);
		Matrix resultMatrix;
		for (int i = 0; i<columnMatrix.getRowDimension(); i++) {
			columnMatrix.set(i, 0, rightSingularMatrix.get(columnNumber, i));
		}
		
		resultMatrix = leftSingularMatrix.times(singularValueMatrix);
		resultMatrix = resultMatrix.times(columnMatrix);
		
		return resultMatrix;
	}
	
	/**
	 * 
	 * @param queryMatrix
	 * @param documentMatrix
	 * @return Similarity value between the query entered by the user and a particular document
	 */
	private double getSimilarity(Matrix queryMatrix, Matrix documentMatrix) {
		Matrix productMatrix;
		double denominator, similarity;
		
		productMatrix = (queryMatrix.transpose()).times(documentMatrix);
		denominator = getModulus(queryMatrix)*getModulus(documentMatrix);
		
		similarity = productMatrix.det()/denominator;
		return similarity;
	}
	
	/**
	 * 
	 * @param queryVector
	 * @return Map where key is the document and value is the similarity it has with the query 
	 */
	private TreeMap<String, Double> findSimilarities(Matrix queryVector) {
		ArrayList<String> documents = invObj.getDocumentList();
		similarityValues = new TreeMap<String, Double>();
		Matrix documentMatrix;
		int columnNumber = 0;
		
		for (String document : documents) {
			documentMatrix = getDocumentColumnMatrix(columnNumber++);
			similarityValues.put(document, getSimilarity(queryVector, documentMatrix)); 
		}
		
		return similarityValues;
	}
	
	/**
	 * 
	 * @param similarityValues
	 * @return Document having maximum similarity value in the map that it receives
	 */
	private String findMax(TreeMap<String, Double> similarityValues) {
		double max = Double.NEGATIVE_INFINITY;
		String maxDocName = null;
		for (String document : similarityValues.keySet()) {
			if (similarityValues.get(document) != Double.NaN && similarityValues.get(document) > max) {
				maxDocName = document;
				max = similarityValues.get(document);
			}
		}
		
		return maxDocName;
	}
	
	/**
	 * 
	 * @param similarityValues
	 * @param docCount
	 * @throws IOException
	 * 
	 * Displays the top <relevantDocs> documents which match the user query
	 */
	private void displayRelevantDocuments(TreeMap<String, Double> similarityValues, int docCount) throws IOException {
		if (docCount > TD.getColumnDimension()) 
			docCount = TD.getColumnDimension();

		System.out.println();
		for (int i = 0; i < docCount; i++) {
			String documentName = findMax(similarityValues);
			if (documentName != null) {
				System.out.println(i + 1 + ". Document: " + documentName + ", Relevance score: " + similarityValues.get(documentName));
				invObj.printWords(documentName);
				similarityValues.remove(documentName);
			} else {
				System.out.println("\nOnly " + i + " relevant documents were there\n");
				return;
			}
		}
	}
	
	/**
	 * 
	 * @param query
	 * @throws IOException
	 * 
	 * Prepares the query entered by the user and then calls various functions to compute similarity and rank documents accordingly.
	 */
	public void handleQuery(String query) throws IOException {
		Matrix queryVector = createQueryVector(query);
		
		if (eligibleQuery == true) {
			TreeMap<String, Double> documentSimilarityValues = findSimilarities(queryVector);
			displayRelevantDocuments(documentSimilarityValues, relevantDocs);
			eligibleQuery = false;
		} else {
			System.out.println("The query you entered consists entirely of stop list words or words that don't appear in any documents");
		}
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String query = null;
		LSI lsiObj = null;
			
		if (args.length != 3 && args.length != 2) {
			System.out.println("Arguments missing. Please enter the required parameters i.e. directory path to documents, path to stop list, number of singular values (optional).\nEXITING");
			System.exit(1);
		}
	
		if (args.length == 2)
			lsiObj = new LSI(args[0], args[1]);
		else {
			try {
				lsiObj = new LSI(args[0], args[1], Integer.parseInt(args[2]));
			} catch (Exception e) {
				System.out.println("\nEnter valid parameters.");
				System.exit(1);
			}
		}
		
		lsiObj.createTermDocumentMatrix();	
		lsiObj.performSingularValueDecomposition();
		while(true) {
			System.out.print("\nEnter your query ");
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			query = br.readLine();
			if (query.equals("ZZZ"))
				break;
			if (query.equals("")) {
				System.out.println("No query entered. Enter some query.");
				continue;
			}
			lsiObj.handleQuery(query);
		}
		System.out.println("\nThank you for trying out the system.");
	}

}
