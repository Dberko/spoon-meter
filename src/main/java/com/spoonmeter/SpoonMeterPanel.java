package com.spoonmeter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class SpoonMeterPanel extends PluginPanel
{
	private final SpoonMeterPlugin plugin;

	private final JLabel verdictLabel = new JLabel();
	private final JLabel scoreLabel = new JLabel();
	private final JLabel blurbLabel = new JLabel();
	private final JLabel statsLabel = new JLabel();
	private final LuckBar overallBar = new LuckBar(10);
	private final JPanel sourceList = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
	private final JComboBox<SortMode> sortBox = new JComboBox<>(SortMode.values());
	private final JLabel footerLabel = new JLabel();

	private SpoonReport report;
	private boolean updatingSortBox;

	public SpoonMeterPanel(SpoonMeterPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel(new DynamicGridLayout(0, 1, 0, 8));
		content.setOpaque(false);
		content.add(buildVerdictCard());
		content.add(buildControls());
		content.add(sourceList);
		content.add(buildFooter());

		sourceList.setOpaque(false);

		add(content, BorderLayout.NORTH);
		showEmpty();
	}

	private JPanel buildVerdictCard()
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(10, 10, 10, 10));

		verdictLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		verdictLabel.setHorizontalAlignment(SwingConstants.CENTER);

		scoreLabel.setFont(FontManager.getRunescapeSmallFont());
		scoreLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);

		blurbLabel.setFont(FontManager.getRunescapeSmallFont());
		blurbLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		blurbLabel.setHorizontalAlignment(SwingConstants.CENTER);

		statsLabel.setFont(FontManager.getRunescapeSmallFont());
		statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statsLabel.setHorizontalAlignment(SwingConstants.CENTER);

		card.add(verdictLabel);
		card.add(scoreLabel);
		card.add(overallBar);
		card.add(statsLabel);
		card.add(blurbLabel);
		return card;
	}

	private JPanel buildControls()
	{
		JPanel controls = new JPanel(new BorderLayout(5, 0));
		controls.setOpaque(false);

		sortBox.setFocusable(false);
		sortBox.setFont(FontManager.getRunescapeSmallFont());
		sortBox.addActionListener(e ->
		{
			if (!updatingSortBox)
			{
				plugin.setSortMode((SortMode) sortBox.getSelectedItem());
			}
		});

		JButton copyButton = new JButton("Copy");
		copyButton.setFocusable(false);
		copyButton.setFont(FontManager.getRunescapeSmallFont());
		copyButton.setToolTipText("Copy the full breakdown to the clipboard");
		copyButton.addActionListener(e -> copyToClipboard());

		// Pasting this into clan chat is the only way other people actually see the rating: the
		// client cannot alter the text it sends, so !spoon renders for you alone.
		JButton chatButton = new JButton("Chat");
		chatButton.setFocusable(false);
		chatButton.setFont(FontManager.getRunescapeSmallFont());
		chatButton.setToolTipText("Copy a one-line version that fits in a game chat message");
		chatButton.addActionListener(e -> copyChatLine());

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		buttons.add(copyButton);
		buttons.add(chatButton);

		controls.add(sortBox, BorderLayout.CENTER);
		controls.add(buttons, BorderLayout.EAST);
		return controls;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);

		footerLabel.setFont(FontManager.getRunescapeSmallFont());
		footerLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		footer.add(footerLabel, BorderLayout.CENTER);
		return footer;
	}

	/** Must be called on the EDT. */
	void update(SpoonReport report, SortMode sortMode)
	{
		this.report = report;

		updatingSortBox = true;
		sortBox.setSelectedItem(sortMode);
		updatingSortBox = false;

		sourceList.removeAll();

		if (report == null || report.isEmpty())
		{
			showEmpty();
			sourceList.revalidate();
			sourceList.repaint();
			return;
		}

		verdictLabel.setText(report.getRating().getTitle());
		verdictLabel.setForeground(report.getRating().getColor());
		scoreLabel.setText("Spoon score " + report.getSpoonScore() + " / 100");
		blurbLabel.setText("<html><div style='text-align:center'>" + report.getRating().getBlurb()
			+ "<br>Luckier than " + report.getSpoonScore() + "% of accounts at your KC.</div></html>");
		statsLabel.setText(SpoonSummary.WHOLE.format(report.getObtained()) + " uniques vs "
			+ SpoonSummary.ONE_DP.format(report.getExpected()) + " expected  ("
			+ SpoonSummary.ONE_DP.format(report.getRatio()) + "x)");
		overallBar.setPercentile(report.getPercentile());

		List<SpoonReport.SourceLine> sources = new ArrayList<>(report.getSources());
		sources.sort(sortMode.getComparator());

		for (SpoonReport.SourceLine source : sources)
		{
			sourceList.add(new SourceRow(source));
		}

		footerLabel.setText("<html>" + sources.size() + " rated of " + report.getPagesSeen()
			+ " pages read.<br>Open more collection log pages to feed the meter."
			+ "<br>* rates vary with team size or points.</html>");

		sourceList.revalidate();
		sourceList.repaint();
	}

	private void showEmpty()
	{
		verdictLabel.setText("No data yet");
		verdictLabel.setForeground(Color.WHITE);
		scoreLabel.setText("");
		statsLabel.setText("");
		overallBar.setPercentile(0.5);
		blurbLabel.setText("<html><div style='text-align:center'>Open your collection log and click "
			+ "through the boss pages you care about. Every page you view is read and remembered.</div></html>");
		footerLabel.setText(report == null ? "" : "<html>" + report.getPagesSeen()
			+ " pages read, none with enough KC to rate yet.</html>");
	}

	private void copyChatLine()
	{
		if (report == null || report.isEmpty())
		{
			return;
		}

		copy(SpoonSummary.chatLine(report, ""));
	}

	private void copyToClipboard()
	{
		if (report == null || report.isEmpty())
		{
			return;
		}

		copy(SpoonSummary.forClipboard(report, (SortMode) sortBox.getSelectedItem()));
	}

	private static void copy(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}
}
