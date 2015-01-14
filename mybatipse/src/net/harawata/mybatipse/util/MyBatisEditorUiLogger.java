
package net.harawata.mybatipse.util;

import net.harawata.mybatipse.Activator;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * @author Peter Hendriks
 * @author Ats Uiboupin
 */
public final class MyBatisEditorUiLogger
{

	private MyBatisEditorUiLogger()
	{
		// No instance needed
	}

	public static void error(String message, Throwable error)
	{
		Activator instance = Activator.getDefault();
		instance.getLog().log(
			new Status(IStatus.ERROR, instance.getBundle().getSymbolicName(), message, error));
	}
}
