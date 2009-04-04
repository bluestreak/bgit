/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spearce.jgit.awtui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Stroke;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.spearce.jgit.awtui.SwingCommitList.SwingLane;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.revplot.PlotCommit;
import org.spearce.jgit.revplot.PlotCommitList;

/**
 * Draws a commit graph in a JTable.
 * <p>
 * This class is currently a very primitive commit visualization tool. It shows
 * a table of 3 columns:
 * <ol>
 * <li>Commit graph and short message</li>
 * <li>Author name and email address</li>
 * <li>Author date and time</li>
 * </ul>
 */
public class CommitGraphPane extends JTable {
	private static final long serialVersionUID = 1L;

	private final SwingCommitList allCommits;

	/** Create a new empty panel. */
	public CommitGraphPane() {
		allCommits = new SwingCommitList();
		configureHeader();
		setShowHorizontalLines(false);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		configureRowHeight();
	}

	private void configureRowHeight() {
		int h = 0;
		for (int i = 0; i<getColumnCount(); ++i) {
			TableCellRenderer renderer = getDefaultRenderer(getColumnClass(i));
			Component c = renderer.getTableCellRendererComponent(this, "ÅOj", false, false, 0, i);
			h = Math.max(h, c.getPreferredSize().height);
		}
		setRowHeight(h + getRowMargin());
	}

	/**
	 * Get the commit list this pane renders from.
	 * 
	 * @return the list the caller must populate.
	 */
	public PlotCommitList getCommitList() {
		return allCommits;
	}

	@Override
	public void setModel(final TableModel dataModel) {
		if (dataModel != null && !(dataModel instanceof CommitTableModel))
			throw new ClassCastException("Must be special table model.");
		super.setModel(dataModel);
	}

	@Override
	protected TableModel createDefaultDataModel() {
		return new CommitTableModel();
	}

	private void configureHeader() {
		final JTableHeader th = getTableHeader();
		final TableColumnModel cols = th.getColumnModel();

		final TableColumn graph = cols.getColumn(0);
		final TableColumn author = cols.getColumn(1);
		final TableColumn date = cols.getColumn(2);

		graph.setHeaderValue("");
		author.setHeaderValue("Author");
		date.setHeaderValue("Date");

		graph.setCellRenderer(new GraphCellRender());
		author.setCellRenderer(new NameCellRender());
		date.setCellRenderer(new DateCellRender());
	}

	class CommitTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;

		PlotCommit<SwingLane> lastCommit;

		PersonIdent lastAuthor;

		public int getColumnCount() {
			return 3;
		}

		public int getRowCount() {
			return allCommits != null ? allCommits.size() : 0;
		}

		public Object getValueAt(final int rowIndex, final int columnIndex) {
			final PlotCommit<SwingLane> c = allCommits.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return c;
			case 1:
				return authorFor(c);
			case 2:
				return authorFor(c);
			default:
				return null;
			}
		}

		PersonIdent authorFor(final PlotCommit<SwingLane> c) {
			if (c != lastCommit) {
				lastCommit = c;
				lastAuthor = c.getAuthorIdent();
			}
			return lastAuthor;
		}
	}

	class NameCellRender extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getTableCellRendererComponent(final JTable table,
				final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final PersonIdent pi = (PersonIdent) value;

			final String valueStr;
			if (pi != null)
				valueStr = pi.getName() + " <" + pi.getEmailAddress() + ">";
			else
				valueStr = "";
			return super.getTableCellRendererComponent(table, valueStr,
					isSelected, hasFocus, row, column);
		}
	}

	class DateCellRender extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		private final DateFormat fmt = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss");

		public Component getTableCellRendererComponent(final JTable table,
				final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final PersonIdent pi = (PersonIdent) value;

			final String valueStr;
			if (pi != null)
				valueStr = fmt.format(pi.getWhen());
			else
				valueStr = "";
			return super.getTableCellRendererComponent(table, valueStr,
					isSelected, hasFocus, row, column);
		}
	}

	class GraphCellRender extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		private final AWTPlotRenderer renderer = new AWTPlotRenderer(this);

		PlotCommit<SwingLane> commit;

		public Component getTableCellRendererComponent(final JTable table,
				final Object value, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			super.getTableCellRendererComponent(table, value, isSelected,
					hasFocus, row, column);
			commit = (PlotCommit<SwingLane>) value;
			return this;
		}

		@Override
		protected void paintComponent(final Graphics inputGraphics) {
			if (inputGraphics == null)
				return;
			renderer.paint(inputGraphics, commit);
		}
	}

	static final Stroke[] strokeCache;

	static {
		strokeCache = new Stroke[4];
		for (int i = 1; i < strokeCache.length; i++)
			strokeCache[i] = new BasicStroke(i);
	}

	static Stroke stroke(final int width) {
		if (width < strokeCache.length)
			return strokeCache[width];
		return new BasicStroke(width);
	}

}
