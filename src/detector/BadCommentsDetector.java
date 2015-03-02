package detector;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.SourceFile;
import edu.umd.cs.findbugs.ba.SourceFinder;

public class BadCommentsDetector extends BytecodeScanningDetector {

	
	private final BugReporter bugReporter;
	private boolean srcInited;
	private String[] sourceLines;
	private String methodName;
	private SourceLineAnnotation srcLineAnnotation;
	
	public BadCommentsDetector(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to initialize the 'has source' flag
	 *
	 * @param classContext the context object for the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		srcInited = false;
		super.visitClassContext(classContext);
	}
	
	@Override
	public void visitMethod(final Method obj) {
		methodName = obj.getName();
		sourceLines = getSourceLines(obj);
		
		out.println("Found method "+methodName);
		out.println(Arrays.toString(sourceLines));
		out.println();
		
		bugReporter.reportBug(new BugInstance(this, "BAD_COMMENTS", NORMAL_PRIORITY).addClass(this).addMethod(this));
	}
	
	/**
	 * reads the sourcefile based on the source line annotation for the method
	 *
	 * @param obj the method object for the currently parsed method
	 *
	 * @return an array of source lines for the method
	 */
	private String[] getSourceLines(Method obj) {

		BufferedReader sourceReader = null;

		if (srcInited)
			return sourceLines;

		try {
			srcLineAnnotation = SourceLineAnnotation.forEntireMethod(getClassContext().getJavaClass(), obj);
			if (srcLineAnnotation != null)
			{
				SourceFinder sourceFinder = AnalysisContext.currentAnalysisContext().getSourceFinder();
				SourceFile sourceFile = sourceFinder.findSourceFile(srcLineAnnotation.getPackageName(), srcLineAnnotation.getSourceFile());
				sourceReader = new BufferedReader(new InputStreamReader(sourceFile.getInputStream(), "UTF-8"));

				List<String> lines = new ArrayList<String>(100);
				String line;
				while ((line = sourceReader.readLine()) != null)
					lines.add(line);
				sourceLines = lines.toArray(new String[lines.size()]);
			}
		} catch (IOException ioe) {
			//noop
		}
		finally {
			try {
				if (sourceReader != null)
					sourceReader.close();
			} catch (IOException ioe2) {
				//noop
			}
		}
		srcInited = true;
		return sourceLines;
	}
	
	
	
	
	private static PrintStream out;

	static {
		try {
			out = new PrintStream(new FileOutputStream("/tmp/findbugsConsole.txt"),true, StandardCharsets.UTF_8.name());
			out.println("Hello source code checker");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
