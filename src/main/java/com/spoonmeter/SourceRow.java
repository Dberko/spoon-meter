package com.spoonmeter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * One boss/raid in the list. Click it to see which item is doing the bullying.
 *
 * <p>Every row puts its long text in {@code BorderLayout.CENTER} and its short value in
 * {@code EAST}. The sidebar is a fixed 225px, and a long name in {@code WEST} keeps its full
 * preferred width and overlaps the value; in {@code CENTER} it gets whatever is left over and
 * ellipsises. The full text is always on the tooltip.
 */
class SourceRow extends JPanel
{
	private static final Color DRY_TEXT = new Color(0xE0A030);
	private static final Color VERY_DRY_TEXT = new Color(0xE05A45);

	private final JPanel details;
	private boolean expanded;

	SourceRow(SpoonReport.SourceLine line)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 8, 6, 8));

		JPanel summary = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		summary.setOpaque(false);

		JPanel titleRow = new JPanel(new BorderLayout(4, 0));
		titleRow.setOpaque(false);

		JLabel name = new JLabel(line.getName() + (line.isApproximate() ? " *" : ""));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel verdict = new JLabel(line.getRating().getTitle());
		verdict.setFont(FontManager.getRunescapeSmallFont());
		verdict.setForeground(line.getRating().getColor());

		titleRow.add(name, BorderLayout.CENTER);
		titleRow.add(verdict, BorderLayout.EAST);

		LuckBar bar = new LuckBar(6);
		bar.setPercentile(line.getPercentile());

		JPanel statsRow = new JPanel(new BorderLayout(4, 0));
		statsRow.setOpaque(false);

		// Just "1,498 kc" - the real counter names ("Corrupted Gauntlet completion count") are far
		// too long for the sidebar, so they live on the tooltip instead.
		JLabel kc = new JLabel(SpoonSummary.WHOLE.format(line.getKc()) + " kc");
		kc.setFont(FontManager.getRunescapeSmallFont());
		kc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel ratio = new JLabel(line.getObtained() + " / " + SpoonSummary.ONE_DP.format(line.getExpected())
			+ "  (" + SpoonSummary.ONE_DP.format(line.getRatio()) + "x)");
		ratio.setFont(FontManager.getRunescapeSmallFont());
		ratio.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		statsRow.add(kc, BorderLayout.WEST);
		statsRow.add(ratio, BorderLayout.EAST);

		summary.add(titleRow);
		summary.add(bar);
		summary.add(statsRow);

		details = buildDetails(line);
		details.setVisible(false);

		add(summary, BorderLayout.NORTH);
		add(details, BorderLayout.CENTER);

		setToolTipText(buildTooltip(line));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				expanded = !expanded;
				details.setVisible(expanded);
				revalidate();
				repaint();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
	}

	private static String buildTooltip(SpoonReport.SourceLine line)
	{
		StringBuilder tip = new StringBuilder("<html>").append(line.getName());

		for (Map.Entry<String, Integer> counter : line.getCounters().entrySet())
		{
			tip.append("<br>").append(counter.getKey()).append(": ")
				.append(SpoonSummary.WHOLE.format(counter.getValue()));
		}

		tip.append("<br>").append(line.getObtained()).append(" uniques vs ")
			.append(SpoonSummary.ONE_DP.format(line.getExpected())).append(" expected")
			.append("<br>Luckier than ").append(LuckRating.spoonScore(line.getPercentile()))
			.append("% of accounts");

		if (line.isApproximate())
		{
			tip.append("<br>* rates vary with team size / points");
		}

		return tip.append("<br>Click for the item breakdown</html>").toString();
	}

	private JPanel buildDetails(SpoonReport.SourceLine line)
	{
		JPanel panel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createCompoundBorder(
			new EmptyBorder(6, 0, 0, 0),
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR)));

		for (SpoonReport.ItemLine item : line.getItems())
		{
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(3, 0, 0, 0));

			JLabel name = new JLabel(item.getName());
			name.setFont(FontManager.getRunescapeSmallFont());

			String right;
			Color colour;

			if (item.getObtained() > 0)
			{
				name.setForeground(Color.WHITE);
				right = item.getObtained() + " / " + SpoonSummary.ONE_DP.format(item.getExpected());
				colour = ColorScheme.LIGHT_GRAY_COLOR;
			}
			else
			{
				name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				long percent = Math.round(item.getDryness() * 100);
				right = percent + "% have it";
				colour = item.getDryness() >= 0.95 ? VERY_DRY_TEXT
					: item.getDryness() >= 0.75 ? DRY_TEXT : ColorScheme.LIGHT_GRAY_COLOR;
			}

			JLabel value = new JLabel(right);
			value.setFont(FontManager.getRunescapeSmallFont());
			value.setForeground(colour);

			row.setToolTipText("<html>" + item.getName()
				+ "<br>1 in " + SpoonSummary.WHOLE.format(Math.round(1.0 / item.getChance())) + " per attempt"
				+ "<br>" + SpoonSummary.ONE_DP.format(item.getExpected()) + " expected, "
				+ item.getObtained() + " obtained</html>");

			row.add(name, BorderLayout.CENTER);
			row.add(value, BorderLayout.EAST);
			panel.add(row);
		}

		return panel;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
