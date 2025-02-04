// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.apache.commons.logging.LogFactory;
import org.openstreetmap.josm.actions.SaveAction;
import org.openstreetmap.josm.actions.search.SearchAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.projection.Epsg4326;
import org.openstreetmap.josm.data.projection.Mercator;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.PleaseWaitDialog;
import org.openstreetmap.josm.gui.SplashScreen;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer.CommandQueueListener;
import org.openstreetmap.josm.gui.preferences.MapPaintPreference;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.OsmUrlToBounds;
import org.openstreetmap.josm.tools.PlatformHook;
import org.openstreetmap.josm.tools.PlatformHookOsx;
import org.openstreetmap.josm.tools.PlatformHookUnixoid;
import org.openstreetmap.josm.tools.PlatformHookWindows;
import org.openstreetmap.josm.tools.Shortcut;

import es.emergya.ui.gis.IMapView;

abstract public class Main {
	/**
	 * Global parent component for all dialogs and message boxes
	 */
	public static Component parent;
	/**
	 * Global application.
	 */
	public static Main main;
	/**
	 * The worker thread slave. This is for executing all long and intensive
	 * calculations. The executed runnables are guaranteed to be executed
	 * separately and sequential.
	 */
	public final static ExecutorService worker = Executors
			.newSingleThreadExecutor();
	/**
	 * Global application preferences
	 */
	public static Preferences pref = new Preferences();
	/**
	 * The global dataset.
	 */
	public static DataSet ds = new DataSet();
	/**
	 * The global paste buffer.
	 */
	public static DataSet pasteBuffer = new DataSet();
	public static Layer pasteSource;
	/**
	 * The projection method used.
	 */
	public static Projection proj;
	/**
	 * The MapFrame. Use setMapFrame to set or clear it.
	 */
	public static MapFrame map;

	/**
	 * The mapView
	 */
	public static IMapView mapView;

	/**
	 * The dialog that gets displayed during background task execution.
	 */
	public static PleaseWaitDialog pleaseWaitDlg;

	/**
	 * True, when in applet mode
	 */
	public static boolean applet = false;

	public UndoRedoHandler undoRedo = new UndoRedoHandler();

	/**
	 * The main menu bar at top of screen.
	 */
	public MainMenu menu = null;
	public static JPanel contentPane = null;

	private static final org.apache.commons.logging.Log log = LogFactory
			.getLog(Main.class);

	static public final void debug(String msg) {
		log.debug(msg);
	}

	static public final void error(String msg, Exception e) {
		log.error(msg, e);
	}

	/**
	 * Platform specific code goes in here. Plugins may replace it, however,
	 * some hooks will be called before any plugins have been loeaded. So if you
	 * need to hook into those early ones, split your class and send the one
	 * with the early hooks to the JOSM team for inclusion.
	 */
	public static PlatformHook platform;

	/**
	 * Set or clear (if passed <code>null</code>) the map.
	 */
	public final void setMapFrame(final MapFrame map) {
		// MapFrame old = Main.map;
		// Main.map = map;
		// panel.setVisible(false);
		// panel.removeAll();
		// if (map != null)
		// map.fillPanel(panel);
		// else {
		// old.destroy();
		// panel.add(gettingStarted, BorderLayout.CENTER);
		// }
		// panel.setVisible(true);
		// redoUndoListener.commandChanged(0,0);
		//
		// PluginHandler.setMapFrame(old, map);
	}

	/**
	 * Remove the specified layer from the map. If it is the last layer, remove
	 * the map as well.
	 */
	public final void removeLayer(final Layer layer) {
		if (map != null) {
			map.mapView.removeLayer(layer);
			if (layer instanceof OsmDataLayer)
				ds = new DataSet();
			if (map.mapView.getAllLayers().isEmpty())
				setMapFrame(null);
		}
	}

	public Main() {
		this(null);
	}

	public Main(SplashScreen splash) {
		main = this;
		// platform = determinePlatformHook();
		platform.startupHook();

		if (splash != null)
			splash.setStatus(tr("Creating main GUI"));
		contentPane = new JPanel(new BorderLayout());

		menu = new MainMenu();

		undoRedo.listenerCommands.add(redoUndoListener);

		// TaggingPresetPreference.initialize();
		MapPaintPreference.initialize();
	}

	/**
	 * Add a new layer to the map. If no map exists, create one.
	 */
	public final void addLayer(final Layer layer) {
		if (map == null) {
			// final MapFrame mapFrame = new MapFrame();
			// setMapFrame(mapFrame);
			// mapFrame.selectMapMode((MapMode)mapFrame.getDefaultButtonAction());
			// mapFrame.setVisible(true);
			// mapFrame.setVisibleDialogs();
			return;
		}
		map.mapView.addLayer(layer);
	}

	/**
	 * @return The edit osm layer. If none exists, it will be created.
	 */
	public final OsmDataLayer editLayer() {
		return map.mapView.editLayer;
	}

	// /////////////////////////////////////////////////////////////////////////
	// Implementation part
	// /////////////////////////////////////////////////////////////////////////

	protected static Rectangle bounds;

	private final CommandQueueListener redoUndoListener = new CommandQueueListener() {
		public void commandChanged(final int queueSize, final int redoSize) {
		}
	};

	/**
	 * Should be called before the main constructor to setup some parameter
	 * stuff
	 * 
	 * @param args
	 *            The parsed argument list.
	 */
	public static void preConstructorInit(Map<String, Collection<String>> args) {
		try {
//			Main.proj = new Epsg4326();
			Main.proj = new Mercator();
		} catch (final Exception e) {
			e.printStackTrace();
			JOptionPane
					.showMessageDialog(
							null,
							tr("The projection could not be read from preferences. Using Mercartor"));
			Main.proj = new Mercator();
		}

		try {
			try {
				String laf = Main.pref.get("laf");
				if (laf != null && laf.length() > 0)
					UIManager.setLookAndFeel(laf);
			} catch (final javax.swing.UnsupportedLookAndFeelException e) {
				log.debug("Look and Feel not supported: "
						+ Main.pref.get("laf"));
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		UIManager.put("OptionPane.okIcon", ImageProvider.get("ok"));
		UIManager.put("OptionPane.yesIcon", UIManager.get("OptionPane.okIcon"));
		UIManager.put("OptionPane.cancelIcon", ImageProvider.get("cancel"));
		UIManager.put("OptionPane.noIcon", UIManager
				.get("OptionPane.cancelIcon"));

		Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
		String geometry = Main.pref.get("gui.geometry");
		if (args.containsKey("geometry")) {
			geometry = args.get("geometry").iterator().next();
		}
		if (geometry.length() != 0) {
			final Matcher m = Pattern.compile(
					"(\\d+)x(\\d+)(([+-])(\\d+)([+-])(\\d+))?").matcher(
					geometry);
			if (m.matches()) {
				int w = Integer.valueOf(m.group(1));
				int h = Integer.valueOf(m.group(2));
				int x = 0, y = 0;
				if (m.group(3) != null) {
					x = Integer.valueOf(m.group(5));
					y = Integer.valueOf(m.group(7));
					if (m.group(4).equals("-"))
						x = screenDimension.width - x - w;
					if (m.group(6).equals("-"))
						y = screenDimension.height - y - h;
				}
				bounds = new Rectangle(x, y, w, h);
				if (!Main.pref.get("gui.geometry").equals(geometry)) {
					// remember this geometry
					Main.pref.put("gui.geometry", geometry);
				}
			} else {
				System.out.println("Ignoring malformed geometry: " + geometry);
			}
		}
		if (bounds == null)
			bounds = !args.containsKey("no-maximize") ? new Rectangle(0, 0,
					screenDimension.width, screenDimension.height)
					: new Rectangle(1000, 740);

		// preinitialize a wait dialog for all early downloads (e.g. via command
		// line)
		pleaseWaitDlg = new PleaseWaitDialog(null);
	}

	public void postConstructorProcessCmdLine(
			Map<String, Collection<String>> args) {
		// initialize the pleaseWaitDialog with the application as parent to
		// handle focus stuff
		pleaseWaitDlg = new PleaseWaitDialog(parent);

		if (args.containsKey("download"))
			for (String s : args.get("download"))
				downloadFromParamString(false, s);
		if (args.containsKey("downloadgps"))
			for (String s : args.get("downloadgps"))
				downloadFromParamString(true, s);
		if (args.containsKey("selection"))
			for (String s : args.get("selection"))
				SearchAction.search(s, SearchAction.SearchMode.add, false,
						false);
	}

	public static boolean breakBecauseUnsavedChanges() {
		Shortcut.savePrefs();
		if (map != null) {
			boolean modified = false;
			boolean uploadedModified = false;
			for (final Layer l : map.mapView.getAllLayers()) {
				if (l instanceof OsmDataLayer
						&& ((OsmDataLayer) l).isModified()) {
					modified = true;
					uploadedModified = ((OsmDataLayer) l).uploadedModified;
					break;
				}
			}
			if (modified) {
				final String msg = uploadedModified ? "\n"
						+ tr("Hint: Some changes came from uploading new data to the server.")
						: "";
				int result = new ExtendedDialog(
						parent,
						tr("Unsaved Changes"),
						new javax.swing.JLabel(
								tr("There are unsaved changes. Discard the changes and continue?")
										+ msg), new String[] {
								tr("Save and Exit"), tr("Discard and Exit"),
								tr("Cancel") }, new String[] { "save.png",
								"exit.png", "cancel.png" }).getValue();

				// Save before exiting
				if (result == 1) {
					Boolean savefailed = false;
					for (final Layer l : map.mapView.getAllLayers()) {
						if (l instanceof OsmDataLayer
								&& ((OsmDataLayer) l).isModified()) {
							SaveAction save = new SaveAction(l);
							if (!save.doSave())
								savefailed = true;
						}
					}
					return savefailed;
				} else if (result != 2) // Cancel exiting unless the 2nd button
										// was clicked
					return true;
			}
		}
		return false;
	}

	private static void downloadFromParamString(final boolean rawGps, String s) {
		if (s.startsWith("http:")) {
			final Bounds b = OsmUrlToBounds.parse(s);
			if (b == null)
				JOptionPane.showMessageDialog(Main.parent, tr(
						"Ignoring malformed URL: \"{0}\"", s));
			else {
				// DownloadTask osmTask =
				// main.menu.download.downloadTasks.get(0);
			}
			return;
		}
	}

	protected static void determinePlatformHook() {
		String os = System.getProperty("os.name");
		if (os == null) {
			System.err
					.println("Your operating system has no name, so I'm guessing its some kind of *nix.");
			platform = new PlatformHookUnixoid();
		} else if (os.toLowerCase().startsWith("windows")) {
			platform = new PlatformHookWindows();
		} else if (os.equals("Linux") || os.equals("Solaris")
				|| os.equals("SunOS") || os.equals("AIX")
				|| os.equals("FreeBSD") || os.equals("NetBSD")
				|| os.equals("OpenBSD")) {
			platform = new PlatformHookUnixoid();
		} else if (os.toLowerCase().startsWith("mac os x")) {
			platform = new PlatformHookOsx();
		} else {
			System.err.println("I don't know your operating system '" + os
					+ "', so I'm guessing its some kind of *nix.");
			platform = new PlatformHookUnixoid();
		}
	}

	static public String getLanguageCodeU() {
		String languageCode = getLanguageCode();
		if (languageCode.equals("en"))
			return "";
		return languageCode.substring(0, 1).toUpperCase()
				+ languageCode.substring(1) + ":";
	}

	static public String getLanguageCode() {
		String full = Locale.getDefault().toString();
		if (full.equals("iw_IL"))
			return "he";
		/* list of non-single codes supported by josm */
		else if (full.equals("en_GB"))
			return full;
		return Locale.getDefault().getLanguage();
	}

	static public void saveGuiGeometry() {
		// save the current window geometry
		String newGeometry = "";
		try {
			if (((JFrame) parent).getExtendedState() == JFrame.NORMAL) {
				Dimension screenDimension = Toolkit.getDefaultToolkit()
						.getScreenSize();
				Rectangle bounds = parent.getBounds();
				int width = (int) bounds.getWidth();
				int height = (int) bounds.getHeight();
				int x = (int) bounds.getX();
				int y = (int) bounds.getY();
				if (width > screenDimension.width)
					width = screenDimension.width;
				if (height > screenDimension.height)
					width = screenDimension.height;
				if (x < 0)
					x = 0;
				if (y < 0)
					y = 0;
				newGeometry = width + "x" + height + "+" + x + "+" + y;
			}
		} catch (Exception e) {
			System.out.println("Failed to save GUI geometry: " + e);
		}
		pref.put("gui.geometry", newGeometry);
	}

	public static PlatformHook getPlatformHook() {
		Main.determinePlatformHook();
		return Main.platform;
	}

	// public static class MapFrame {
	// public MapView mapView;
	// public MapMode mapMode;
	// public void fillPanel(JPanel p){}
	// public void destroy(){}
	// public void selectMapMode(MapMode m){mapMode = m;}
	// public void repaint() {
	// this.mapView.repaint();
	// }
	// }
}
