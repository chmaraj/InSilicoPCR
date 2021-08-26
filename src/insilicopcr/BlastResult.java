package insilicopcr;

public class BlastResult {
	
	private String name;
	private String queryID;
	private String subjectID;
	private int mismatch;
	private int start;
	private int end;
	private int length;
	private String seq;

	public BlastResult(String name, String queryID, String subjectID, int mismatch, int start, int end, int length, String seq) {
		this.name = name;
		this.queryID = queryID;
		this.subjectID = subjectID;
		this.mismatch = mismatch;
		this.start = start;
		this.end = end;
		this.length = length;
		this.seq = seq;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getQueryID() {
		return this.queryID;
	}
	
	public String getSubjectID() {
		return this.subjectID;
	}
	
	public int getMismatch() {
		return this.mismatch;
	}
	
	public int getStart() {
		return this.start;
	}
	
	public int getEnd() {
		return this.end;
	}
	
	public int getLength() {
		return this.length;
	}
	
	public String getSeq() {
		return this.seq;
	}
}