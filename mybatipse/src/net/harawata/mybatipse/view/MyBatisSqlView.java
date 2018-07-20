/*-******************************************************************************
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Peter Hendriks - author of MyBatis Editor. original idea and implementation. 
 *    Ats Uiboupin - contributed the initial patch for porting.
 *    Iwao AVE! - ported the view with adjustment.
 *******************************************************************************/

package net.harawata.mybatipse.view;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.xml.core.internal.document.AttrImpl;
import org.eclipse.wst.xml.core.internal.document.ElementImpl;
import org.eclipse.wst.xml.core.internal.document.TextImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.util.XpathUtil;

@SuppressWarnings("restriction")
public class MyBatisSqlView extends ViewPart
{
	private static final String BACKGROUND_PREF_ID = "net.harawata.mybatipse.ui.mybatissqlviewBackground";

	private static final String TEXTCOLOR_PREF_ID = "net.harawata.mybatipse.ui.mybatissqlviewTextColor";

	private static final String FONT_PREF_ID = "net.harawata.mybatipse.ui.mybatissqlviewFont";

	private MyBatisSqlViewPropertyChangeListener themeListener;

	private MyBatisSqlViewSelectionListener selectionListener;

	protected Text text;

	@Override
	public void createPartControl(Composite parent)
	{
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new FillLayout());
		text = new Text(composite, SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
		setAppearance();

		themeListener = new MyBatisSqlViewPropertyChangeListener();
		PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(themeListener);

		selectionListener = new MyBatisSqlViewSelectionListener();
		IWorkbenchWindow workbenchWindow = getSite().getWorkbenchWindow();
		workbenchWindow.getSelectionService().addPostSelectionListener(selectionListener);
		setActiveEditorSelection(workbenchWindow);
	}

	private void setActiveEditorSelection(IWorkbenchWindow workbenchWindow)
	{
		// Use the active editor, instead of the global selection.
		// The global selection might be altered by another view.
		IEditorPart activeEditor = workbenchWindow.getActivePage().getActiveEditor();
		if (activeEditor != null)
		{
			ITextEditor editor = (ITextEditor)activeEditor.getAdapter(ITextEditor.class);
			if (editor != null)
			{
				ISelectionProvider provider = editor.getSelectionProvider();
				if (provider != null)
				{
					selectionListener.selectionChanged(editor, provider.getSelection());
				}
			}
		}
	}

	@Override
	public void setFocus()
	{
	}

	@Override
	public void dispose()
	{
		if (selectionListener != null)
		{
			getSite().getWorkbenchWindow()
				.getSelectionService()
				.removePostSelectionListener(selectionListener);
			selectionListener = null;
		}
		if (themeListener != null)
		{
			PlatformUI.getWorkbench().getThemeManager().removePropertyChangeListener(themeListener);
			themeListener = null;
		}
		super.dispose();
	}

	private final class MyBatisSqlViewSelectionListener implements ISelectionListener
	{

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection)
		{
			if (!selection.isEmpty() && (selection instanceof IStructuredSelection))
			{
				IStructuredSelection sel = (IStructuredSelection)selection;
				if (sel.size() == 1)
				{
					handleSingleSelection(sel);
				}
			}
		}

		private void handleSingleSelection(IStructuredSelection sel)
		{
			Object firstElement = sel.getFirstElement();
			if (firstElement != null && firstElement instanceof Node)
			{
				Node node = (Node)firstElement;
				if (node instanceof AttrImpl)
				{
					node = ((AttrImpl)node).getOwnerElement();
				}
				while (node != null && !(node instanceof ElementImpl))
				{
					node = node.getParentNode();
				}
				if (node != null)
				{
					Node statementNode = MybatipseXmlUtil.findEnclosingStatementNode(node);
					if (statementNode != null)
					{
						StringBuilder buffer = new StringBuilder();
						computeStatementText((ElementImpl)statementNode, buffer);
						text.setText(buffer.toString());
					}
				}
			}
		}

		private void computeStatementText(ElementImpl currentNode, StringBuilder buffer)
		{
			if (currentNode == null)
				return;

			NodeList childNodes = currentNode.getChildNodes();
			for (int k = 0; k < childNodes.getLength(); k++)
			{
				Node childNode = childNodes.item(k);
				if (childNode instanceof TextImpl)
				{
					String text = ((TextImpl)childNode).getTextContent();
					buffer.append(text);
				}
				else if (childNode instanceof ElementImpl)
				{
					ElementImpl element = (ElementImpl)childNode;
					String elemName = element.getNodeName();
					if (element.hasChildNodes())
					{
						IStructuredDocumentRegion startRegion = element.getStartStructuredDocumentRegion();
						if (startRegion != null)
							buffer.append(startRegion.getText());
						computeStatementText(element, buffer);
						IStructuredDocumentRegion endRegion = element.getEndStructuredDocumentRegion();
						if (endRegion != null)
							buffer.append(endRegion.getText());
					}
					else if ("include".equals(elemName))
					{
						ElementImpl sqlElement = resolveInclude(element, buffer);
						computeStatementText(sqlElement, buffer);
					}
					else
					{
						buffer.append(element.getSource());
					}
				}
			}
		}

		private ElementImpl resolveInclude(ElementImpl includeElement, StringBuilder buffer)
		{
			String refId = includeElement.getAttribute("refid");
			if (refId.indexOf('$') > -1)
				return null;

			int lastDot = refId.lastIndexOf('.');
			try
			{
				if (lastDot == -1)
				{
					// Internal reference.
					Document domDoc = includeElement.getOwnerDocument();
					return (ElementImpl)XpathUtil.xpathNode(domDoc, "//sql[@id='" + refId + "']");
				}
				else if (lastDot + 1 < refId.length())
				{
					// External reference.
					IJavaProject project = MybatipseXmlUtil
						.getJavaProject(includeElement.getStructuredDocument());
					String namespace = refId.substring(0, lastDot);
					String sqlId = refId.substring(lastDot + 1);
					for (IFile mapperFile : MapperNamespaceCache.getInstance()
						.get(project, namespace, null))
					{
						IDOMDocument mapperDocument = MybatipseXmlUtil.getMapperDocument(mapperFile);
						ElementImpl element = (ElementImpl)XpathUtil.xpathNode(mapperDocument,
							"//sql[@id='" + sqlId + "']");
						if (element != null)
							return element;
					}
				}
			}
			catch (XPathExpressionException e)
			{
				Activator.log(Status.ERROR, "Failed to resolve included sql element.", e);
			}
			return null;
		}
	}

	private final class MyBatisSqlViewPropertyChangeListener implements IPropertyChangeListener
	{
		@Override
		public void propertyChange(PropertyChangeEvent event)
		{
			if (FONT_PREF_ID.equals(event.getProperty())
				|| BACKGROUND_PREF_ID.equals(event.getProperty())
				|| TEXTCOLOR_PREF_ID.equals(event.getProperty()))
			{
				final Display display = getSite().getPage()
					.getWorkbenchWindow()
					.getWorkbench()
					.getDisplay();
				if (!display.isDisposed())
				{
					display.asyncExec(new Runnable()
					{
						public void run()
						{
							if (!display.isDisposed())
							{
								setAppearance();
							}
						}
					});
				}
			}
		}
	}

	protected void setAppearance()
	{
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		if (!text.isDisposed())
		{
			text.setFont(currentTheme.getFontRegistry().get(FONT_PREF_ID));
			text.setBackground(currentTheme.getColorRegistry().get(BACKGROUND_PREF_ID));
			text.setForeground(currentTheme.getColorRegistry().get(TEXTCOLOR_PREF_ID));
		}
	}
}
