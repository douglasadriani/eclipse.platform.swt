/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;


import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cairo.*;
import org.eclipse.swt.internal.gtk.*;

/**
 * Instances of this class represent popup windows that are used
 * to inform or warn the user.
 * <p>
 * <dl>
 * <dt><b>Styles:</b></dt>
 * <dd>BALLOON, ICON_ERROR, ICON_INFORMATION, ICON_WARNING</dd>
 * <dt><b>Events:</b></dt>
 * <dd>Selection</dd>
 * </dl>
 * </p><p>
 * Note: Only one of the styles ICON_ERROR, ICON_INFORMATION,
 * and ICON_WARNING may be specified.
 * </p><p>
 * IMPORTANT: This class is <em>not</em> intended to be subclassed.
 * </p>
 *
 * @see <a href="http://www.eclipse.org/swt/snippets/#tooltips">Tool Tips snippets</a>
 * @see <a href="http://www.eclipse.org/swt/examples.php">SWT Example: ControlExample</a>
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further information</a>
 *
 * @since 3.2
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ToolTip extends Widget {
	Shell parent;
	String text, message;
	TrayItem item;
	int x, y, timerId;
	long /*int*/ layoutText = 0, layoutMessage = 0;
	long /*int*/ provider;
	int [] borderPolygon;
	boolean spikeAbove, autohide;

	static final int BORDER = 5;
	static final int PADDING = 5;
	static final int INSET = 4;
	static final int TIP_HEIGHT = 20;
	static final int IMAGE_SIZE = 16;
	static final int DELAY = 8000;

/**
 * Constructs a new instance of this class given its parent
 * and a style value describing its behavior and appearance.
 * <p>
 * The style value is either one of the style constants defined in
 * class <code>SWT</code> which is applicable to instances of this
 * class, or must be built by <em>bitwise OR</em>'ing together
 * (that is, using the <code>int</code> "|" operator) two or more
 * of those <code>SWT</code> style constants. The class description
 * lists the style constants that are applicable to the class.
 * Style bits are also inherited from superclasses.
 * </p>
 *
 * @param parent a composite control which will be the parent of the new instance (cannot be null)
 * @param style the style of control to construct
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the parent is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
 *    <li>ERROR_INVALID_SUBCLASS - if this class is not an allowed subclass</li>
 * </ul>
 *
 * @see SWT#BALLOON
 * @see SWT#ICON_ERROR
 * @see SWT#ICON_INFORMATION
 * @see SWT#ICON_WARNING
 * @see Widget#checkSubclass
 * @see Widget#getStyle
 */
public ToolTip (Shell parent, int style) {
	super (parent, checkStyle (style));
	this.parent = parent;
	createWidget (0);
	parent.addToolTip (this);
}

static int checkStyle (int style) {
	int mask = SWT.ICON_ERROR | SWT.ICON_INFORMATION | SWT.ICON_WARNING;
	if ((style & mask) == 0) return style;
	return checkBits (style, SWT.ICON_INFORMATION, SWT.ICON_WARNING, SWT.ICON_ERROR, 0, 0, 0);
}

/**
 * Adds the listener to the collection of listeners who will
 * be notified when the receiver is selected by the user, by sending
 * it one of the messages defined in the <code>SelectionListener</code>
 * interface.
 * <p>
 * <code>widgetSelected</code> is called when the receiver is selected.
 * <code>widgetDefaultSelected</code> is not called.
 * </p>
 *
 * @param listener the listener which should be notified when the receiver is selected by the user
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @see SelectionListener
 * @see #removeSelectionListener
 * @see SelectionEvent
 */
public void addSelectionListener (SelectionListener listener) {
	checkWidget ();
	if (listener == null) error (SWT.ERROR_NULL_ARGUMENT);
	TypedListener typedListener = new TypedListener (listener);
	addListener (SWT.Selection,typedListener);
	addListener (SWT.DefaultSelection,typedListener);
}

void configure () {
	long /*int*/ screen = OS.gdk_screen_get_default ();
	OS.gtk_widget_realize (handle);
	/*
	 * Feature in GTK: using gdk_screen_get_monitor_at_window() does not accurately get the correct monitor on the machine.
	 * Using gdk_screen_get_monitor_at_point() returns it correctly. Using getLocation() on point will get
	 * the coordinates for where the tooltip should appear on the host.
	 */
	Point point = getLocation();
	boolean multipleMonitors = (OS.gdk_screen_get_n_monitors(screen) > 1) ? true : false;
	int monitorNumber = OS.gdk_screen_get_monitor_at_point(screen, point.x, point.y);
	GdkRectangle dest = new GdkRectangle ();
	OS.gdk_screen_get_monitor_geometry (screen, monitorNumber, dest);
	point = getSize (dest.width / 4);
	int w = point.x;
	int h = point.y;
	point = getLocation ();
	int x = point.x;
	int y = point.y;
	OS.gtk_window_resize (handle, w, h + TIP_HEIGHT);
	int[] polyline;
	spikeAbove = dest.height >= y + h + TIP_HEIGHT;
	if ((dest.width >= x + w) || (multipleMonitors && OS.gdk_screen_width() >= x + w)) {
		if (dest.height >= y + h + TIP_HEIGHT) {
			int t = TIP_HEIGHT;
			polyline = new int[] {
				0, 5+t, 1, 5+t, 1, 3+t, 3, 1+t,  5, 1+t, 5, t,
				16, t, 16, 0, 35, t,
				w-5, t, w-5, 1+t, w-3, 1+t, w-1, 3+t, w-1, 5+t, w, 5+t,
				w, h-5+t, w-1, h-5+t, w-1, h-3+t, w-2, h-3+t, w-2, h-2+t, w-3, h-2+t, w-3, h-1+t, w-5, h-1+t, w-5, h+t,
				5, h+t, 5, h-1+t, 3, h-1+t, 3, h-2+t, 2, h-2+t, 2, h-3+t, 1, h-3+t, 1, h-5+t, 0, h-5+t,
				0, 5+t};
			borderPolygon = new int[] {
				0, 5+t, 1, 4+t, 1, 3+t, 3, 1+t,  4, 1+t, 5, t,
				16, t, 16, 1, 35, t,
				w-6, 0+t, w-5, 1+t, w-4, 1+t, w-2, 3+t, w-2, 4+t, w-1, 5+t,
				w-1, h-6+t, w-2, h-5+t, w-2, h-4+t, w-4, h-2+t, w-5, h-2+t, w-6, h-1+t,
				5, h-1+t, 4, h-2+t, 3, h-2+t, 1, h-4+t, 1, h-5+t, 0, h-6+t,
				0, 5+t};
			if ((parent.style & SWT.MIRRORED) != 0) {
				x -= w - 36;
				polyline[12] = w-36;
				polyline[14] = w-16;
				polyline[16] = w-15;
				borderPolygon[12] = w-35;
				borderPolygon[14] = borderPolygon[16]  = w-16;
			}
			OS.gtk_window_move (handle, Math.max(0, x - 17), y);
		} else {
			polyline = new int[] {
				0, 5, 1, 5, 1, 3, 3, 1,  5, 1, 5, 0,
				w-5, 0, w-5, 1, w-3, 1, w-1, 3, w-1, 5, w, 5,
				w, h-5, w-1, h-5, w-1, h-3, w-2, h-3, w-2, h-2, w-3, h-2, w-3, h-1, w-5, h-1, w-5, h,
				35, h, 16, h+TIP_HEIGHT, 16, h,
				5, h, 5, h-1, 3, h-1, 3, h-2, 2, h-2, 2, h-3, 1, h-3, 1, h-5, 0, h-5,
				0, 5};
			borderPolygon = new int[] {
				0, 5, 1, 4, 1, 3, 3, 1,  4, 1, 5, 0,
				w-6, 0, w-5, 1, w-4, 1, w-2, 3, w-2, 4, w-1, 5,
				w-1, h-6, w-2, h-5, w-2, h-4, w-4, h-2, w-5, h-2, w-6, h-1,
				35, h-1, 17, h+TIP_HEIGHT-2, 17, h-1,
				5, h-1, 4, h-2, 3, h-2, 1, h-4, 1, h-5, 0, h-6,
				0, 5};
			if ((parent.style & SWT.MIRRORED) != 0) {
				x -= w - 36;
				polyline [42] =  polyline [44] =  w-16;
				polyline [46] = w-35;
				borderPolygon[36] = borderPolygon[38] = w-17;
				borderPolygon [40] = w-35;
			}
			OS.gtk_window_move (handle, Math.max(0, x - 17), y - h - TIP_HEIGHT);
		}
	} else {
		if (dest.height >= y + h + TIP_HEIGHT) {
			int t = TIP_HEIGHT;
			polyline = new int[] {
				0, 5+t, 1, 5+t, 1, 3+t, 3, 1+t,  5, 1+t, 5, t,
				w-35, t, w-16, 0, w-16, t,
				w-5, t, w-5, 1+t, w-3, 1+t, w-1, 3+t, w-1, 5+t, w, 5+t,
				w, h-5+t, w-1, h-5+t, w-1, h-3+t, w-2, h-3+t, w-2, h-2+t, w-3, h-2+t, w-3, h-1+t, w-5, h-1+t, w-5, h+t,
				5, h+t, 5, h-1+t, 3, h-1+t, 3, h-2+t, 2, h-2+t, 2, h-3+t, 1, h-3+t, 1, h-5+t, 0, h-5+t,
				0, 5+t};
			borderPolygon = new int[] {
				0, 5+t, 1, 4+t, 1, 3+t, 3, 1+t,  4, 1+t, 5, t,
				w-35, t, w-17, 2, w-17, t,
				w-6, t, w-5, 1+t, w-4, 1+t, w-2, 3+t, w-2, 4+t, w-1, 5+t,
				w-1, h-6+t, w-2, h-5+t, w-2, h-4+t, w-4, h-2+t, w-5, h-2+t, w-6, h-1+t,
				5, h-1+t, 4, h-2+t, 3, h-2+t, 1, h-4+t, 1, h-5+t, 0, h-6+t,
				0, 5+t};
			if ((parent.style & SWT.MIRRORED) != 0) {
				x += w - 35;
				polyline [12] = polyline [14] = 16;
				polyline [16] = 35;
				borderPolygon[12] =  borderPolygon[14] = 16;
				borderPolygon [16] = 35;
			}
			OS.gtk_window_move (handle, Math.max(dest.width- w, x - w + 17), y);
		} else {
			polyline = new int[] {
				0, 5, 1, 5, 1, 3, 3, 1,  5, 1, 5, 0,
				w-5, 0, w-5, 1, w-3, 1, w-1, 3, w-1, 5, w, 5,
				w, h-5, w-1, h-5, w-1, h-3, w-2, h-3, w-2, h-2, w-3, h-2, w-3, h-1, w-5, h-1, w-5, h,
				w-16, h, w-16, h+TIP_HEIGHT, w-35, h,
				5, h, 5, h-1, 3, h-1, 3, h-2, 2, h-2, 2, h-3, 1, h-3, 1, h-5, 0, h-5,
				0, 5};
			borderPolygon = new int[] {
				0, 5, 1, 4, 1, 3, 3, 1,  4, 1, 5, 0,
				w-6, 0, w-5, 1, w-4, 1, w-2, 3, w-2, 4, w-1, 5,
				w-1, h-6, w-2, h-5, w-2, h-4, w-4, h-2, w-5, h-2, w-6, h-1,
				w-17, h-1, w-17, h+TIP_HEIGHT-2, w-36, h-1,
				5, h-1, 4, h-2, 3, h-2, 1, h-4, 1, h-5, 0, h-6,
				0, 5};
			if ((parent.style & SWT.MIRRORED) != 0) {
				x += w - 35;
				polyline [42] =  35;
				polyline [44] = polyline [46] = 16;
				borderPolygon[36] = 35;
				borderPolygon[38] = borderPolygon [40] = 17;
			}
			OS.gtk_window_move (handle, Math.max(dest.width - w, x - w + 17), y - h - TIP_HEIGHT);
		}
	}
	OS.gtk_widget_realize(handle);
	Region region = new Region (display);
	region.add(DPIUtil.autoScaleDown(polyline));
	if (OS.GTK3) {
		OS.gtk_widget_shape_combine_region (handle, region.handle);
	} else {
		long /*int*/ window = gtk_widget_get_window (handle);
		OS.gdk_window_shape_combine_region (window, region.handle, 0, 0);
	 }
	region.dispose ();
}

@Override
void createHandle (int index) {
	if ((style & SWT.BALLOON) != 0) {
		state |= HANDLE;
		handle = OS.gtk_window_new (OS.GTK_WINDOW_POPUP);
		Color background = display.getSystemColor (SWT.COLOR_INFO_BACKGROUND);
		if (OS.GTK3) {
			long /*int*/ context = OS.gtk_widget_get_style_context (handle);
			GdkRGBA bgRGBA = display.toGdkRGBA(display.COLOR_INFO_BACKGROUND);
			String name = OS.GTK_VERSION >= OS.VERSION(3, 20, 0) ? "window" : "GtkWindow";
			String css = name + " {background-color: " + display.gtk_rgba_to_css_string(bgRGBA) + ";}";
			gtk_css_provider_load_from_css (context, css);
			OS.gtk_style_context_invalidate (context);
		} else {
			OS.gtk_widget_modify_bg (handle, OS.GTK_STATE_NORMAL, background.handle);
		}
		OS.gtk_window_set_type_hint (handle, OS.GDK_WINDOW_TYPE_HINT_TOOLTIP);
	}
}

void gtk_css_provider_load_from_css (long /*int*/ context, String css) {
	/* Utility function. */
	//@param css : a 'css java' string like "{\nbackground: red;\n}".
	if (provider == 0) {
		provider = OS.gtk_css_provider_new ();
		OS.gtk_style_context_add_provider (context, provider, OS.GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
		OS.g_object_unref (provider);
	}
	OS.gtk_css_provider_load_from_data (provider, Converter.wcsToMbcs (null, css, true), -1, null);
}

@Override
void createWidget (int index) {
	super.createWidget (index);
	text = "";
	message = "";
	x = y = -1;
	autohide = true;
}

@Override
void destroyWidget () {
	long /*int*/ topHandle = topHandle ();
	if (parent != null) parent.removeTooTip (this);
	releaseHandle ();
	if (topHandle != 0 && (state & HANDLE) != 0) {
		if ((style & SWT.BALLOON) != 0) {
			OS.gtk_widget_destroy (topHandle);
		} else {
			OS.g_object_unref (topHandle);
		}
	}
}

/**
 * Returns <code>true</code> if the receiver is automatically
 * hidden by the platform, and <code>false</code> otherwise.
 *
 * @return the receiver's auto hide state
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 */
public boolean getAutoHide () {
	checkWidget ();
	return autohide;
}

Point getLocation () {
	int x = this.x;
	int y = this.y;
	if (item != null) {
		long /*int*/ itemHandle = item.handle;
		GdkRectangle area = new GdkRectangle ();
		OS.gtk_status_icon_get_geometry (itemHandle, 0, area, 0);
		x = area.x + area.width / 2;
		y = area.y + area.height / 2;
	}
	if (x == -1 || y == -1) {
		int [] px = new int [1], py = new int [1];
		gdk_window_get_device_position (0, px, py, null);
		x = px [0];
		y = py [0];
	}
	return new Point(x, y);
}

/**
 * Returns the receiver's message, which will be an empty
 * string if it has never been set.
 *
 * @return the receiver's message
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public String getMessage () {
	checkWidget ();
	return message;
}

@Override
String getNameText () {
	return getText ();
}

/**
 * Returns the receiver's parent, which must be a <code>Shell</code>.
 *
 * @return the receiver's parent
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public Shell getParent () {
	checkWidget ();
	return parent;
}

Point getSize (int maxWidth) {
	int textWidth = 0, messageWidth = 0;
	int [] w = new int [1], h = new int [1];
	if (layoutText != 0) {
		OS.pango_layout_set_width (layoutText, -1);
		OS.pango_layout_get_pixel_size (layoutText, w, h);
		textWidth = w [0];
	}
	if (layoutMessage != 0) {
		OS.pango_layout_set_width (layoutMessage, -1);
		OS.pango_layout_get_pixel_size (layoutMessage, w, h);
		messageWidth = w [0];
	}
	int messageTrim = 2 * INSET + 2 * BORDER + 2 * PADDING;
	boolean hasImage = layoutText != 0 && (style & (SWT.ICON_ERROR | SWT.ICON_INFORMATION | SWT.ICON_WARNING)) != 0;
	int textTrim = messageTrim + (hasImage ? IMAGE_SIZE : 0);
	int width = Math.min (maxWidth, Math.max (textWidth + textTrim, messageWidth + messageTrim));
	int textHeight = 0, messageHeight = 0;
	if (layoutText != 0) {
		OS.pango_layout_set_width (layoutText, (maxWidth - textTrim) * OS.PANGO_SCALE);
		OS.pango_layout_get_pixel_size (layoutText, w, h);
		textHeight = h [0];
	}
	if (layoutMessage != 0) {
		OS.pango_layout_set_width (layoutMessage, (maxWidth - messageTrim) * OS.PANGO_SCALE);
		OS.pango_layout_get_pixel_size (layoutMessage, w, h);
		messageHeight = h [0];
	}
	int height = 2 * BORDER + 2 * PADDING + messageHeight;
	if (layoutText != 0) height += Math.max (IMAGE_SIZE, textHeight) + 2 * PADDING;
	return new Point(width, height);
}

/**
 * Returns the receiver's text, which will be an empty
 * string if it has never been set.
 *
 * @return the receiver's text
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public String getText () {
	checkWidget ();
	return text;
}

/**
 * Returns <code>true</code> if the receiver is visible, and
 * <code>false</code> otherwise.
 * <p>
 * If one of the receiver's ancestors is not visible or some
 * other condition makes the receiver not visible, this method
 * may still indicate that it is considered visible even though
 * it may not actually be showing.
 * </p>
 *
 * @return the receiver's visibility state
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public boolean getVisible () {
	checkWidget ();
	if ((style & SWT.BALLOON) != 0) return OS.gtk_widget_get_visible (handle);
	return false;
}

@Override
long /*int*/ gtk_button_press_event (long /*int*/ widget, long /*int*/ event) {
	sendSelectionEvent (SWT.Selection, null, true);
	setVisible (false);
	return 0;
}

void drawTooltip (long /*int*/ cr) {
	long /*int*/ window = gtk_widget_get_window (handle);
	int x = BORDER + PADDING;
	int y = BORDER + PADDING;
	if (OS.USE_CAIRO) {
		long /*int*/ cairo = cr != 0 ? cr : OS.gdk_cairo_create(window);
		if (cairo == 0) error (SWT.ERROR_NO_HANDLES);
		int count = borderPolygon.length / 2;
		if (count != 0) {
			Cairo.cairo_set_line_width(cairo, 1);
			Cairo.cairo_move_to(cairo, borderPolygon[0], borderPolygon[1]);
			for (int i=1,j=2; i<count; i++,j+=2) {
				Cairo.cairo_line_to(cairo, borderPolygon[j]+0.5, borderPolygon[j+1]+0.5);
			}
			Cairo.cairo_close_path(cairo);
			Cairo.cairo_stroke(cairo);
		}
		if (spikeAbove) y += TIP_HEIGHT;
		if (layoutText != 0) {
			byte[] buffer = null;
			int id = style & (SWT.ICON_ERROR | SWT.ICON_INFORMATION | SWT.ICON_WARNING);
			switch (id) {
				case SWT.ICON_ERROR: buffer = Converter.wcsToMbcs (null, "gtk-dialog-error", true); break;
				case SWT.ICON_INFORMATION: buffer = Converter.wcsToMbcs (null, "gtk-dialog-info", true); break;
				case SWT.ICON_WARNING: buffer = Converter.wcsToMbcs (null, "gtk-dialog-warning", true); break;
			}
			if (buffer != null) {
				long /*int*/ pixbuf, icon_set = OS.gtk_icon_factory_lookup_default (buffer);
				if (OS.GTK3) {
					pixbuf = OS.gtk_icon_set_render_icon_pixbuf(icon_set, OS.gtk_widget_get_style_context(handle), OS.GTK_ICON_SIZE_MENU);
				} else {
					long /*int*/ style = OS.gtk_widget_get_default_style ();
					pixbuf = OS.gtk_icon_set_render_icon (icon_set, style, OS.GTK_TEXT_DIR_NONE, OS.GTK_STATE_NORMAL, OS.GTK_ICON_SIZE_MENU, 0, 0);
				}
 				OS.gdk_cairo_set_source_pixbuf(cairo, pixbuf, x, y);
 				Cairo.cairo_paint (cairo);
				OS.g_object_unref (pixbuf);
				x += IMAGE_SIZE;
			}
			x += INSET;
			int [] w = new int [1], h = new int [1];
			Color foreground = display.getSystemColor (SWT.COLOR_INFO_FOREGROUND);
			OS.gdk_cairo_set_source_color(cairo,foreground.handle);
			Cairo.cairo_move_to(cairo, x,y );
			OS.pango_cairo_show_layout(cairo, layoutText);
			OS.pango_layout_get_pixel_size (layoutText, w, h);
			y += 2 * PADDING + Math.max (IMAGE_SIZE, h [0]);
		}
		if (layoutMessage != 0) {
			x = BORDER + PADDING + INSET;
			Color foreground = display.getSystemColor (SWT.COLOR_INFO_FOREGROUND);
			OS.gdk_cairo_set_source_color(cairo,foreground.handle);
			Cairo.cairo_move_to(cairo, x, y);
			OS.pango_cairo_show_layout(cairo, layoutMessage);
		}
		if (cairo != cr) Cairo.cairo_destroy(cairo);
		return;
	}
	long /*int*/ gdkGC = OS.gdk_gc_new (window);
	OS.gdk_draw_polygon (window, gdkGC, 0, borderPolygon, borderPolygon.length / 2);
	if (spikeAbove) y += TIP_HEIGHT;
	if (layoutText != 0) {
		byte[] buffer = null;
		int id = style & (SWT.ICON_ERROR | SWT.ICON_INFORMATION | SWT.ICON_WARNING);
		switch (id) {
			case SWT.ICON_ERROR: buffer = Converter.wcsToMbcs (null, "gtk-dialog-error", true); break;
			case SWT.ICON_INFORMATION: buffer = Converter.wcsToMbcs (null, "gtk-dialog-info", true); break;
			case SWT.ICON_WARNING: buffer = Converter.wcsToMbcs (null, "gtk-dialog-warning", true); break;
		}
		if (buffer != null) {
			long /*int*/ style = OS.gtk_widget_get_default_style ();
			long /*int*/ pixbuf = OS.gtk_icon_set_render_icon (
				OS.gtk_icon_factory_lookup_default (buffer),
				style,
				OS.GTK_TEXT_DIR_NONE,
				OS.GTK_STATE_NORMAL,
				OS.GTK_ICON_SIZE_MENU,
				0,
				0);
			OS.gdk_draw_pixbuf (window, gdkGC, pixbuf, 0, 0, x, y, IMAGE_SIZE, IMAGE_SIZE, OS.GDK_RGB_DITHER_NORMAL, 0, 0);
			OS.g_object_unref (pixbuf);
			x += IMAGE_SIZE;
		}
		x += INSET;
		Color foreground = display.getSystemColor (SWT.COLOR_INFO_FOREGROUND);
		OS.gdk_gc_set_foreground (gdkGC, foreground.handle);
		OS.gdk_draw_layout (window, gdkGC, x, y, layoutText);
		int [] w = new int [1], h = new int [1];
		OS.pango_layout_get_pixel_size (layoutText, w, h);
		y += 2 * PADDING + Math.max (IMAGE_SIZE, h [0]);
	}
	if (layoutMessage != 0) {
		x = BORDER + PADDING + INSET;
		Color foreground = display.getSystemColor (SWT.COLOR_INFO_FOREGROUND);
		OS.gdk_gc_set_foreground (gdkGC, foreground.handle);
		OS.gdk_draw_layout (window, gdkGC, x, y, layoutMessage);
	}
	OS.g_object_unref (gdkGC);
}

@Override
long /*int*/ gtk_draw (long /*int*/ widget, long /*int*/ cairo) {
	if ((state & OBSCURED) != 0) return 0;
	drawTooltip (cairo);
	return 0;
}

@Override
long /*int*/ gtk_expose_event (long /*int*/ widget, long /*int*/ eventPtr) {
	if ((state & OBSCURED) != 0) return 0;
	drawTooltip (0);
	return 0;
}

@Override
long /*int*/ gtk_size_allocate (long /*int*/ widget, long /*int*/ allocation) {
	Point point = getLocation ();
	int x = point.x;
	int y = point.y;
	long /*int*/ screen = OS.gdk_screen_get_default ();
	OS.gtk_widget_realize (widget);
	int monitorNumber = OS.gdk_screen_get_monitor_at_point(screen, point.x, point.y);
	GdkRectangle dest = new GdkRectangle ();
	OS.gdk_screen_get_monitor_geometry (screen, monitorNumber, dest);
	GtkAllocation widgetAllocation = new GtkAllocation ();
	OS.gtk_widget_get_allocation (widget, widgetAllocation);
	int w = widgetAllocation.width;
	int h = widgetAllocation.height;
	if (dest.height < y + h) y -= h;
	if (dest.width < x + w) x -= w;
	OS.gtk_window_move (widget, x, y);
	return 0;
}

@Override
void hookEvents () {
	if ((style & SWT.BALLOON) != 0) {
		OS.g_signal_connect_closure_by_id (handle, display.signalIds [EXPOSE_EVENT], 0, display.getClosure (EXPOSE_EVENT), true);
		if (OS.GTK_VERSION >= OS.VERSION (3, 9, 0)) {
			OS.g_signal_connect_closure_by_id (handle, display.signalIds [EXPOSE_EVENT_INVERSE], 0, display.getClosure (EXPOSE_EVENT_INVERSE), true);
		}
		OS.gtk_widget_add_events (handle, OS.GDK_BUTTON_PRESS_MASK);
		OS.g_signal_connect_closure (handle, OS.button_press_event, display.getClosure (BUTTON_PRESS_EVENT), false);
	}
}

/**
 * Returns <code>true</code> if the receiver is visible and all
 * of the receiver's ancestors are visible and <code>false</code>
 * otherwise.
 *
 * @return the receiver's visibility state
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @see #getVisible
 */
public boolean isVisible () {
	checkWidget ();
	return getVisible ();
}

@Override
void releaseWidget () {
	super.releaseWidget ();
	setVisible(false);
	if (layoutText != 0) OS.g_object_unref (layoutText);
	layoutText = 0;
	if (layoutMessage != 0) OS.g_object_unref (layoutMessage);
	layoutMessage = 0;
	if (timerId != 0) OS.g_source_remove(timerId);
	timerId = 0;
	text = null;
	message = null;
	borderPolygon = null;
}

/**
 * Removes the listener from the collection of listeners who will
 * be notified when the receiver is selected by the user.
 *
 * @param listener the listener which should no longer be notified
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @see SelectionListener
 * @see #addSelectionListener
 */
public void removeSelectionListener (SelectionListener listener) {
	checkWidget();
	if (listener == null) error (SWT.ERROR_NULL_ARGUMENT);
	if (eventTable == null) return;
	eventTable.unhook (SWT.Selection, listener);
	eventTable.unhook (SWT.DefaultSelection, listener);
}

/**
 * Makes the receiver hide automatically when <code>true</code>,
 * and remain visible when <code>false</code>.
 *
 * @param autoHide the auto hide state
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @see #getVisible
 * @see #setVisible
 */
public void setAutoHide (boolean autoHide) {
	checkWidget ();
	this.autohide = autoHide;
	//TODO - update when visible
}

/**
 * Sets the location of the receiver, which must be a tooltip,
 * to the point specified by the arguments which are relative
 * to the display.
 * <p>
 * Note that this is different from most widgets where the
 * location of the widget is relative to the parent.
 * </p>
 *
 * @param x the new x coordinate for the receiver
 * @param y the new y coordinate for the receiver
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setLocation (int x, int y) {
	checkWidget ();
	setLocation (new Point (x, y));
}

void setLocationInPixels (int x, int y) {
	checkWidget ();
	this.x = x;
	this.y = y;
	if ((style & SWT.BALLOON) != 0) {
		if (OS.gtk_widget_get_visible (handle)) configure ();
	}
}
/**
 * Sets the location of the receiver, which must be a tooltip,
 * to the point specified by the argument which is relative
 * to the display.
 * <p>
 * Note that this is different from most widgets where the
 * location of the widget is relative to the parent.
 * </p><p>
 * Note that the platform window manager ultimately has control
 * over the location of tooltips.
 * </p>
 *
 * @param location the new location for the receiver
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the point is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setLocation (Point location) {
	checkWidget ();
	setLocationInPixels(DPIUtil.autoScaleUp(location));
}

void setLocationInPixels (Point location) {
	checkWidget ();
	if (location == null) error (SWT.ERROR_NULL_ARGUMENT);
	setLocationInPixels (location.x, location.y);
}

/**
 * Sets the receiver's message.
 *
 * @param string the new message
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the text is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setMessage (String string) {
	checkWidget ();
	if (string == null) error (SWT.ERROR_NULL_ARGUMENT);
	message = string;
	if ((style & SWT.BALLOON) == 0) return;
	if (layoutMessage != 0) OS.g_object_unref (layoutMessage);
	layoutMessage = 0;
	if (message.length () != 0) {
		byte [] buffer = Converter.wcsToMbcs (null, message, true);
		layoutMessage = OS.gtk_widget_create_pango_layout (handle, buffer);
		OS.pango_layout_set_auto_dir (layoutMessage, false);
		OS.pango_layout_set_wrap (layoutMessage, OS.PANGO_WRAP_WORD_CHAR);
	}
	if (OS.gtk_widget_get_visible (handle)) configure ();
}

/**
 * Sets the receiver's text.
 *
 * @param string the new text
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the text is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setText (String string) {
	checkWidget ();
	if (string == null) error (SWT.ERROR_NULL_ARGUMENT);
	text = string;
	if ((style & SWT.BALLOON) == 0) return;
	if (layoutText != 0) OS.g_object_unref (layoutText);
	layoutText = 0;
	if (text.length () != 0) {
		byte [] buffer = Converter.wcsToMbcs (null, text, true);
		layoutText = OS.gtk_widget_create_pango_layout (handle, buffer);
		OS.pango_layout_set_auto_dir (layoutText, false);
		long /*int*/ boldAttr = OS.pango_attr_weight_new (OS.PANGO_WEIGHT_BOLD);
		PangoAttribute attribute = new PangoAttribute ();
		OS.memmove (attribute, boldAttr, PangoAttribute.sizeof);
		attribute.start_index = 0;
		attribute.end_index = buffer.length;
		OS.memmove (boldAttr, attribute, PangoAttribute.sizeof);
		long /*int*/ attrList = OS.pango_attr_list_new ();
		OS.pango_attr_list_insert (attrList, boldAttr);
		OS.pango_layout_set_attributes (layoutText, attrList);
		OS.pango_attr_list_unref (attrList);
		OS.pango_layout_set_wrap (layoutText, OS.PANGO_WRAP_WORD_CHAR);
	}
	if (OS.gtk_widget_get_visible (handle)) configure ();
}

/**
 * Marks the receiver as visible if the argument is <code>true</code>,
 * and marks it invisible otherwise.
 * <p>
 * If one of the receiver's ancestors is not visible or some
 * other condition makes the receiver not visible, marking
 * it visible may not actually cause it to be displayed.
 * </p>
 *
 * @param visible the new visibility state
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setVisible (boolean visible) {
	checkWidget ();
	if (timerId != 0) OS.g_source_remove(timerId);
	timerId = 0;
	if (visible) {
		if ((style & SWT.BALLOON) != 0) {
			configure ();
			OS.gtk_widget_show (handle);
		} else {
			long /*int*/ vboxHandle = parent.vboxHandle;
			StringBuffer string = new StringBuffer (text);
			if (text.length () > 0) string.append ("\n\n");
			string.append (message);
			byte [] buffer = Converter.wcsToMbcs (null, string.toString(), true);
			OS.gtk_widget_set_tooltip_text(vboxHandle, buffer);
		}
		if (autohide) timerId = OS.g_timeout_add (DELAY, display.windowTimerProc, handle);
	} else {
		if ((style & SWT.BALLOON) != 0) {
			OS.gtk_widget_hide (handle);
		} else {
			long /*int*/ vboxHandle = parent.vboxHandle;
			byte[] buffer = Converter.wcsToMbcs(null, "", true);
			OS.gtk_widget_set_tooltip_text(vboxHandle, buffer);
		}
	}
}

@Override
long /*int*/ timerProc (long /*int*/ widget) {
	if ((style & SWT.BALLOON) != 0) {
		OS.gtk_widget_hide (handle);
	}
	return 0;
}

}
