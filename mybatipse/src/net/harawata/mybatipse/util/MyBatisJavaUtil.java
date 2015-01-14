
package net.harawata.mybatipse.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

/**
 * @author Peter Hendriks
 */
public class MyBatisJavaUtil
{

	private static final String JAVANATURE_ID = "org.eclipse.jdt.core.javanature";

	public static IContainer[] getSourceFolders(IProject project) throws CoreException
	{
		if (project.hasNature(JAVANATURE_ID))
		{
			IClasspathEntry[] rawClasspath = JavaCore.create(project).getRawClasspath();
			List<IContainer> result = new ArrayList<IContainer>();
			for (IClasspathEntry entry : rawClasspath)
			{
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE)
				{
					result.add((IContainer)getResource(entry.getPath()));
				}
			}
			return (IContainer[])result.toArray(new IContainer[result.size()]);
		}
		else
		{
			return new IContainer[]{
				project
			};
		}
	}

	public static IType findJavaType(IProject project, String className) throws CoreException
	{
		if (!project.hasNature(JAVANATURE_ID))
		{
			return null;
		}
		return JavaCore.create(project).findType(className);
	}

	public static List<ICompletionProposal> proposeJavaTypes(IProject project, int offset)
		throws CoreException
	{
		if (!project.hasNature(JAVANATURE_ID))
		{
			return Collections.emptyList();
		}
		return Collections.emptyList();
	}

	public static IJavaElement findJavaElement(IProject project, String className,
		String elementName) throws CoreException
	{
		IType type = findJavaType(project, className);
		if (type != null)
		{
			for (IMethod method : type.getMethods())
			{
				if (method.getElementName().equals(elementName))
				{
					return method;
				}
			}
		}
		return null;
	}

	public static IResource getResource(IPath path)
	{
		return ResourcesPlugin.getWorkspace().getRoot().findMember(path);
	}
}
