/*-******************************************************************************
 * Copyright (c) 2016 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.quickassist;

import java.util.List;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.quickassist.JavaQuickAssistProcessor.MapperMethod;

final class AddParamQuickAssist extends QuickAssistCompletionProposal
{
	private CompilationUnit astNode;

	private MapperMethod method;

	@SuppressWarnings("unchecked")
	@Override
	public void apply(IDocument document)
	{
		List<SingleVariableDeclaration> params = method.parameters();
		for (SingleVariableDeclaration param : params)
		{
			List<IExtendedModifier> modifiers = param.modifiers();
			if (!hasParamAnnotation(modifiers))
			{
				if (MybatipseConstants.TYPE_ROW_BOUNDS
					.equals(param.resolveBinding().getType().getQualifiedName()))
					continue;
				AST ast = param.getAST();
				SingleMemberAnnotation annotation = ast.newSingleMemberAnnotation();
				annotation.setTypeName(ast.newName("Param"));
				StringLiteral paramValue = ast.newStringLiteral();
				paramValue.setLiteralValue(param.getName().getFullyQualifiedName());
				annotation.setValue(paramValue);
				param.modifiers().add(annotation);
			}
		}
		TextEdit textEdit = astNode.rewrite(document, null);
		try
		{
			textEdit.apply(document);
		}
		catch (MalformedTreeException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		catch (BadLocationException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	private boolean hasParamAnnotation(List<IExtendedModifier> modifiers)
	{
		for (IExtendedModifier modifier : modifiers)
		{
			if (modifier.isAnnotation()
				&& "Param".equals(((Annotation)modifier).getTypeName().getFullyQualifiedName()))
			{
				return true;
			}
		}
		return false;
	}

	public AddParamQuickAssist(String displayString, MapperMethod method, CompilationUnit astNode)
	{
		super(displayString);
		this.method = method;
		this.astNode = astNode;
	}
}
