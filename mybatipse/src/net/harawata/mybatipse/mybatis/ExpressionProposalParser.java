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

/**
 * @author Iwao AVE!
 */
public class ExpressionProposalParser
{
	private final String text;

	private final int offset;

	private String matchString;

	private String proposalTarget;

	private boolean proposable;

	public ExpressionProposalParser(String text, int offset)
	{
		this.text = text;
		this.offset = offset;
		parse();
	}

	private void parse()
	{
		StringBuilder buffer = new StringBuilder();
		int trailingWhitespaceMode = 0;
		for (int i = offset; i > 0; i--)
		{
			char c = text.charAt(i);
			if (c <= 0x20)
			{
				if (trailingWhitespaceMode == 0)
					trailingWhitespaceMode = 1;
			}
			else if (Character.isJavaIdentifierPart(c) || c == '[' || c == ']' || c == '.')
			{
				if (trailingWhitespaceMode == 1)
					break;
				buffer.insert(0, c);
			}
			else if (c == ',')
			{
				trailingWhitespaceMode = 2;
				if (matchString == null)
				{
					matchString = buffer.toString();
					buffer.setLength(0);
				}
				if (proposalTarget == null)
				{
					proposalTarget = buffer.toString();
					buffer.setLength(0);
				}
			}
			else if (c == '=')
			{
				trailingWhitespaceMode = 2;
				if (matchString == null)
				{
					matchString = buffer.toString();
				}
				buffer.setLength(0);
			}
			else if (c == '{' && i > 0)
			{
				char preChr = text.charAt(i - 1);
				if (preChr == '#' || preChr == '$')
				{
					proposable = true;
					if (matchString == null)
					{
						proposalTarget = "property";
						matchString = buffer.toString();
					}
					else if (preChr == '$')
					{
						proposable = false;
					}
					break;
				}
			}
			else
			{
				break;
			}
		}
	}

	public int getReplacementLength()
	{
		boolean inWhitespace = false;
		int i = offset + 1;
		for (; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (Character.isJavaIdentifierPart(c) || c == '[' || c == ']' || c == '.')
			{
				if (inWhitespace)
					return i - offset - 1 + matchString.length();
			}
			else if (c <= 0x20)
			{
				if (c == 0x0A || c == 0x0D)
					return i - offset - 1 + matchString.length();
				inWhitespace = true;
			}
			else
			{
				break;
			}
		}
		return i - offset - 1 + matchString.length();
	}

	public String getMatchString()
	{
		return matchString;
	}

	public String getProposalTarget()
	{
		return proposalTarget;
	}

	public boolean isProposable()
	{
		return proposable;
	}
}
