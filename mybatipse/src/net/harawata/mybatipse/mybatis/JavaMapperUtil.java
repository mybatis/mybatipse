/*-******************************************************************************
 * Copyright (c) 2014 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

/**
 * @author Iwao AVE!
 */
public class JavaMapperUtil
{
	public static final String TYPE_ROW_BOUNDS = "org.apache.ibatis.session.RowBounds";

	private static final String ANNOTATION_PARAM = "org.apache.ibatis.annotations.Param";

	public static Map<String, String> getMethodParameters(ASTNode node, IMethod method)
	{
		Map<String, String> results = new HashMap<String, String>();

		MethodDeclaration methodDeclaration = getMethodDeclaration(node, method);
		if (methodDeclaration == null)
			return results;

		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> params = methodDeclaration.parameters();
		for (int i = 0; i < params.size(); i++)
		{
			IVariableBinding paramBinding = params.get(i).resolveBinding();
			String paramFqn = paramBinding.getType().getQualifiedName();
			if (TYPE_ROW_BOUNDS.equals(paramFqn))
				continue;
			IAnnotationBinding[] annotations = paramBinding.getAnnotations();
			for (IAnnotationBinding annotation : annotations)
			{
				if (ANNOTATION_PARAM.equals(annotation.getAnnotationType().getQualifiedName()))
				{
					IMemberValuePairBinding[] valuePairs = annotation.getAllMemberValuePairs();
					if (valuePairs.length == 1)
					{
						IMemberValuePairBinding valuePairBinding = valuePairs[0];
						String paramValue = (String)valuePairBinding.getValue();
						results.put(paramValue, paramFqn);
					}
				}
			}
			results.put("param" + (i + 1), paramFqn); //$NON-NLS-1$
		}
		return results;
	}

	public static MethodDeclaration getMethodDeclaration(ASTNode node, IMethod method)
	{
		if (method == null)
			return null;

		StatementVisitor visitor = new StatementVisitor(method);
		node.accept(visitor);
		return visitor.getResult();
	}

	private static class StatementVisitor extends ASTVisitor
	{
		private MethodDeclaration result;

		private IMethod targetMethod;

		public StatementVisitor(IMethod targetMethod)
		{
			this.targetMethod = targetMethod;
		}

		@Override
		public boolean visit(MethodDeclaration node)
		{
			if (targetMethod.getElementName().equals(node.getName().getFullyQualifiedName()))
			{
				IMethod method = (IMethod)node.resolveBinding().getJavaElement();
				if (targetMethod.isSimilar(method))
					result = node;
			}
			return false;
		}

		public MethodDeclaration getResult()
		{
			return result;
		}
	}

	public static CompilationUnit getAstNode(ICompilationUnit compilationUnit)
	{
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(compilationUnit);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		return (CompilationUnit)parser.createAST(new NullProgressMonitor());
	}
}
