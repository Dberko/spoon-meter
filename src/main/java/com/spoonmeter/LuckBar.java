package com.spoonmeter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * A cursed-to-spooned strip with a marker at the account's percentile. Reading a percentile off a
 * bar is a lot faster than reading it off a number, and the colour does the editorialising.
 */
class LuckBar extends JComponent
{
	private static final Color CURSED = new Color(0xC62828);
	private static final Color MIDDLE = new Color(0x9E9E9E);
	private static final Color SPOONED = new Color(0xFFC107);

	private double percentile;

	LuckBar(int height)
	{
		setPreferredSize(new Dimension(0, height));
		setMinimumSize(new Dimension(0, height));
	}

	void setPercentile(double percentile)
	{
		this.percentile = Math.max(0, Math.min(1, percentile));
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();

		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth();
			int h = getHeight();
			int arc = Math.min(h, 8);

			g2.setColor(ColorScheme.DARK_GRAY_COLOR);
			g2.fillRoundRect(0, 0, w, h, arc, arc);

			g2.setPaint(new GradientPaint(0, 0, CURSED, w / 2f, 0, MIDDLE));
			g2.fillRoundRect(0, 0, w / 2, h, arc, arc);

			g2.setPaint(new GradientPaint(w / 2f, 0, MIDDLE, w, 0, SPOONED));
			g2.fillRoundRect(w / 2, 0, w - w / 2, h, arc, arc);

			int x = (int) Math.round(percentile * (w - 3));
			g2.setColor(Color.WHITE);
			g2.fillRect(x, -1, 3, h + 2);
			g2.setColor(new Color(0, 0, 0, 120));
			g2.drawRect(x, -1, 3, h + 2);
		}
		finally
		{
			g2.dispose();
		}
	}
}
