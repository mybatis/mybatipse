/*-******************************************************************************
 * Copyright (c) 2016 ave.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    ave - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.groovy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.eclipse.codeassist.processors.IProposalProvider;
import org.codehaus.groovy.eclipse.codeassist.proposals.IGroovyProposal;
import org.codehaus.groovy.eclipse.codeassist.requestor.ContentAssistContext;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

import net.harawata.mybatipse.mybatis.JavaCompletionProposalComputer;

/**
 * @author ave
 */
public class GroovyProposalProvider extends JavaCompletionProposalComputer
	implements IProposalProvider
{

	@Override
	public List<String> getNewFieldProposals(ContentAssistContext arg0)
	{
		return Collections.emptyList();
	}

	@Override
	public List<MethodNode> getNewMethodProposals(ContentAssistContext arg0)
	{
		return Collections.emptyList();
	}

	@Override
	public List<IGroovyProposal> getStatementAndExpressionProposals(
		ContentAssistContext paramContentAssistContext, ClassNode paramClassNode,
		boolean paramBoolean, Set<ClassNode> paramSet)
	{
		GroovyCompilationUnit unit = paramContentAssistContext.unit;
		int offset = paramContentAssistContext.completionLocation;
		List<IGroovyProposal> proposals = new ArrayList<IGroovyProposal>();
		List<ICompletionProposal> javaProposals = computeJavaProposals(unit, offset);
		for (ICompletionProposal javaProposal : javaProposals)
		{
			if (javaProposal instanceof IJavaCompletionProposal)
			{
				proposals.add(new GroovyProposal((IJavaCompletionProposal)javaProposal));
			}
		}
		return proposals;
	}

	class GroovyProposal implements IGroovyProposal
	{
		private IJavaCompletionProposal javaProposal;

		public GroovyProposal(IJavaCompletionProposal javaProposal)
		{
			super();
			this.javaProposal = javaProposal;
		}

		@Override
		public IJavaCompletionProposal createJavaProposal(
			ContentAssistContext paramContentAssistContext,
			JavaContentAssistInvocationContext paramJavaContentAssistInvocationContext)
		{
			return javaProposal;
		}
	}
}
