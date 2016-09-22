package test.helper.rules;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class SGTestWatcher extends TestWatcher{
  
	@Override
  protected void starting(final Description description) {
      final String methodName = description.getMethodName();
      String className = description.getClassName();
      className = className.substring(className.lastIndexOf('.') + 1);
      System.out.println("---------------- Starting JUnit-test: " + className + " " + methodName + " ----------------");
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
      final String methodName = description.getMethodName();
      String className = description.getClassName();
      className = className.substring(className.lastIndexOf('.') + 1);
      System.out.println(">>>> " + className + " " + methodName + " FAILED due to " + e);
  }

  @Override
  protected void finished(final Description description) {
      // System.out.println("-----------------------------------------------------------------------------------------");
  }

}
