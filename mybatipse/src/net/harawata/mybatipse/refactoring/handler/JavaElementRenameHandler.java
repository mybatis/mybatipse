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

package net.harawata.mybatipse.refactoring.handler;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import net.harawata.mybatipse.refactoring.ElementRenameInfo;
import net.harawata.mybatipse.refactoring.ElementRenameRefactoring;
import net.harawata.mybatipse.refactoring.collector.ResultMapRenameEditCollector;
import net.harawata.mybatipse.refactoring.collector.StatementRenameEditCollector;

/**
 * @author Iwao AVE!
 */
public class JavaElementRenameHandler extends ElementRenameHandler
{
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		workbenchWindow = HandlerUtil.getActiveWorkbenchWindow(event);
		if (workbenchWindow == null)
			return null;

		IWorkbenchPage activePage = workbenchWindow.getActivePage();
		if (activePage == null)
			return null;

		editor = HandlerUtil.getActiveEditor(event);
		if (editor == null)
			return null;

		IJavaElement element = JavaUI.getEditorInputJavaElement(editor.getEditorInput());
		final ICompilationUnit compilationUnit = element.getAdapter(ICompilationUnit.class);
		final ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
		final int offset = ((ITextSelection)textEditor.getSelectionProvider().getSelection())
			.getOffset();
		final ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		parser.setIgnoreMethodBodies(true);
		final CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		JavaElementRenameVisitor visitor = new JavaElementRenameVisitor(
			compilationUnit.getJavaProject(), offset);
		astUnit.accept(visitor);
		if (visitor.getRefactoring() != null)
		{
			runRefactoringWizard(visitor.getRefactoringInfo(), visitor.getRefactoring());
		}
		return null;
	}

	private final class JavaElementRenameVisitor extends ASTVisitor
	{
		private final int offset;

		private final IJavaProject project;

		private MethodDeclaration method;

		private ElementRenameRefactoring refactoring;

		private ElementRenameInfo info;

		public JavaElementRenameVisitor(IJavaProject project, int offset)
		{
			this.project = project;
			this.offset = offset;
		}

		@Override
		public boolean visit(MethodDeclaration node)
		{
			if (!isInRange(node, offset))
				return false;
			method = node;
			return true;
		}

		@Override
		public boolean visit(SingleMemberAnnotation anno)
		{
			if (!isInRange(anno, offset))
				return false;
			parseAnnotation(anno);
			return false;
		}

		@Override
		public boolean visit(NormalAnnotation anno)
		{
			if (!isInRange(anno, offset))
				return false;
			parseAnnotation(anno);
			return false;
		}

		@SuppressWarnings("unchecked")
		private void parseAnnotation(Annotation anno)
		{
			String name = anno.getTypeName().getFullyQualifiedName();
			if ("ResultMap".equals(name))
			{
				info = createRefactoringInfo(
					stringAt(annotationValueAt(anno, "value", offset), offset));
				if (info != null)
					refactoring = new ElementRenameRefactoring(new ResultMapRenameEditCollector(info));
			}
			else if ("Results".equals(name))
			{
				info = createRefactoringInfo(stringAt(annotationValueAt(anno, "id", offset), offset));
				if (info != null)
				{
					refactoring = new ElementRenameRefactoring(new ResultMapRenameEditCollector(info));
				}
				else
				{
					// Look for select statement reference.
					Expression value = annotationValueAt(anno, "value", offset);
					List<Expression> resultAnnos;
					if (value.getNodeType() == Expression.ARRAY_INITIALIZER)
					{
						resultAnnos = ((ArrayInitializer)value).expressions();
					}
					else
					{
						resultAnnos = Arrays.asList((Expression)value);
					}
					for (Expression resultAnno : resultAnnos)
					{
						if (isInRange(resultAnno, offset))
						{
							info = createRefactoringInfo(
								stringAt(annotationValueAt((Annotation)annotationValueAt((Annotation)resultAnno,
									Arrays.asList("one", "many"), offset), "select", offset), offset));
							IMethod method = findMapperMethod(info);
							if (method == null)
							{
								refactoring = new ElementRenameRefactoring(
									new StatementRenameEditCollector(info, true));
							}
							else
							{
								invokeJdtRename(method);
							}
							break;
						}
					}
				}
			}
		}

		private ElementRenameInfo createRefactoringInfo(String id)
		{
			if (id == null)
				return null;

			String oldId;
			String namespace;
			int namespaceEnd = id.lastIndexOf('.');
			if (namespaceEnd > -1)
			{
				oldId = id.substring(namespaceEnd + 1);
				namespace = id.substring(0, namespaceEnd);
			}
			else
			{
				oldId = id;
				namespace = method.resolveBinding().getDeclaringClass().getBinaryName();
			}
			ElementRenameInfo refactoringInfo = new ElementRenameInfo();
			refactoringInfo.setOldId(oldId);
			refactoringInfo.setNamespace(namespace);
			refactoringInfo.setProject(project);
			return refactoringInfo;
		}

		private Expression annotationValueAt(Annotation anno, String targetKey, final int offset)
		{
			return annotationValueAt(anno, Arrays.asList(targetKey), offset);
		}

		private Expression annotationValueAt(Annotation anno, List<String> targetKeys,
			final int offset)
		{
			if (anno.isSingleMemberAnnotation())
			{
				Expression value = ((SingleMemberAnnotation)anno).getValue();
				if (targetKeys.indexOf("value") > -1 && isInRange(value, offset))
					return value;
			}
			else if (anno.isNormalAnnotation())
			{
				@SuppressWarnings("unchecked")
				List<MemberValuePair> pairs = ((NormalAnnotation)anno).values();
				for (MemberValuePair pair : pairs)
				{
					SimpleName key = pair.getName();
					Expression value = pair.getValue();
					if (targetKeys.indexOf(key.getFullyQualifiedName()) > -1 && isInRange(value, offset))
						return value;
				}
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		private String stringAt(Expression value, int offset)
		{
			if (value == null)
				return null;

			if (value.getNodeType() == Expression.ARRAY_INITIALIZER)
			{
				ArrayInitializer arrayInitializer = (ArrayInitializer)value;
				for (Expression expression : (List<Expression>)arrayInitializer.expressions())
				{
					if (isInRange(expression, offset))
					{
						return (String)expression.resolveConstantExpressionValue();
					}
				}
			}
			else if (value.getNodeType() == Expression.STRING_LITERAL)
			{
				return (String)value.resolveConstantExpressionValue();
			}
			return null;
		}

		private boolean isInRange(ASTNode node, int offset)
		{
			int start = node.getStartPosition();
			return start <= offset && offset <= start + node.getLength();
		}

		public ElementRenameInfo getRefactoringInfo()
		{
			return info;
		}

		public ElementRenameRefactoring getRefactoring()
		{
			return refactoring;
		}
	}
}
