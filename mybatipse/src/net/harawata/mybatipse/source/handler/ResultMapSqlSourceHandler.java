/*-******************************************************************************
 * Copyright (c) 2018 Sc122.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sc122 - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.source.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Parent {@link AbstractHandler} for implementation of &lt;sql&gt; statement generation.
 * Statements generated should be as generic as possible, taking into account the functionality
 * described at:
 * <p>
 * {@code http://www.mybatis.org/mybatis-3/sqlmap-xml.html }
 * <p>
 * 
 * @author kdavidson
 */
public abstract class ResultMapSqlSourceHandler extends AbstractHandler
{

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		if (!isEnabled())
			return null;

		IWorkbenchPage activePage = PlatformUI.getWorkbench()
			.getActiveWorkbenchWindow()
			.getActivePage();

		// HandlerUtil.getCurrentSelection(event) does not return
		// the latest 'selection' when the cursor is moved.
		ISelection selection = activePage.getSelection();
		Element resultMap = (Element)((IStructuredSelection)selection).getFirstElement();

		return addSqlElement(resultMap);
	}

	@Override
	public boolean isEnabled()
	{
		IWorkbenchWindow workbench = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (workbench != null)
		{
			IWorkbenchPage activePage = workbench.getActivePage();

			if (activePage != null)
			{
				ISelection selection = activePage.getSelection();
				if (selection != null && selection instanceof IStructuredSelection
					&& selection instanceof ITextSelection)
				{
					Object selected = ((IStructuredSelection)selection).getFirstElement();
					if (selected instanceof Element)
					{
						final Element ele = (Element)selected;
						if ("resultMap".equals(ele.getTagName()))
						{
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * Created and add the SQL element.
	 * 
	 * @param resultMap
	 * @return
	 */
	protected Element addSqlElement(Element resultMap)
	{
		Document document = resultMap.getOwnerDocument();
		Element sql = document.createElement("sql");
		sql.setAttribute("id", buildSqlId(resultMap));
		sql.appendChild(document.createTextNode(buildSqlStatement(resultMap)));

		// TODO: Update with properties for Overwriting current or adding version suffix
		Node mapper = document.getElementsByTagName("mapper").item(0);
		Node next = resultMap.getNextSibling();
		mapper.insertBefore(sql, next);
		mapper.insertBefore(document.createTextNode("\n\n"), sql);

		return sql;
	}

	protected String nodeColumnValue(Node node)
	{
		Node col = node.getAttributes().getNamedItem("column") != null
			? node.getAttributes().getNamedItem("column")
			: node.getAttributes().getNamedItem("property");

		return col.getNodeValue();
	}

	protected String nodeParameterValue(Node node)
	{
		StringBuilder sb = new StringBuilder();

		Node col = node.getAttributes().getNamedItem("property");
		sb.append(col.getNodeValue());

		if (node.getAttributes().getNamedItem("jdbcType") != null)
		{
			col = node.getAttributes().getNamedItem("jdbcType");
			sb.append(String.format(", %s=%s", col.getNodeName(), col.getNodeValue()));
		}

		if (node.getAttributes().getNamedItem("javaType") != null)
		{
			col = node.getAttributes().getNamedItem("javaType");
			sb.append(String.format(", %s=%s", col.getNodeName(), col.getNodeValue()));
		}

		return sb.toString();
	}

	protected abstract String buildSqlId(Element resultMap);

	protected abstract String buildSqlStatement(Element resultMap);

}
