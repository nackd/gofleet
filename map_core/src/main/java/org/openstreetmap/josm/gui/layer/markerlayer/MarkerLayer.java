// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm.gui.layer.markerlayer;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Icon;
import javax.swing.JColorChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.GpxLink;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.AudioPlayer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * A layer holding markers.
 * 
 * Markers are GPS points with a name and, optionally, a symbol code attached;
 * marker layers can be created from waypoints when importing raw GPS data, but
 * they may also come from other sources.
 * 
 * The symbol code is for future use.
 * 
 * The data is read only.
 */
public class MarkerLayer extends Layer {

	/**
	 * A list of markers.
	 */
	public Collection<Marker> data;
	private boolean mousePressed = false;
	public GpxLayer fromLayer = null;
	private MapView mapView = null;

	/*
	 * private Icon audioTracerIcon = null; private EastNorth playheadPosition =
	 * null; private static Timer timer = null; private static double
	 * audioAnimationInterval = 0.0; // seconds private static double
	 * playheadTime = -1.0;
	 */
	public MarkerLayer(GpxData indata, String name, File associatedFile,
			GpxLayer fromLayer, MapView mapView) {

		super(name);
		this.setAssociatedFile(associatedFile);
		this.data = new ArrayList<Marker>();
		this.fromLayer = fromLayer;
		this.setMapView(mapView);
		double firstTime = -1.0;
		String lastLinkedFile = "";

		for (WayPoint wpt : indata.waypoints) {
			/* calculate time differences in waypoints */
			double time = wpt.time;
			boolean wpt_has_link = wpt.attr.containsKey(GpxData.META_LINKS);
			if (firstTime < 0 && wpt_has_link) {
				firstTime = time;
				for (GpxLink oneLink : (Collection<GpxLink>) wpt.attr
						.get(GpxData.META_LINKS)) {
					lastLinkedFile = oneLink.uri;
					break;
				}
			}
			if (wpt_has_link) {
				for (GpxLink oneLink : (Collection<GpxLink>) wpt.attr
						.get(GpxData.META_LINKS)) {
					if (!oneLink.uri.equals(lastLinkedFile))
						firstTime = time;
					lastLinkedFile = oneLink.uri;
					break;
				}
			}
			Marker m = Marker.createMarker(wpt, indata.storageFile, this, time,
					time - firstTime);
			if (m != null)
				data.add(m);
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				MarkerLayer.this.mapView.addMouseListener(new MouseAdapter() {
					@Override
					public void mousePressed(MouseEvent e) {
						if (e.getButton() != MouseEvent.BUTTON1)
							return;
						boolean mousePressedInButton = false;
						if (e.getPoint() != null) {
							for (Marker mkr : data) {
								if (mkr.containsPoint(e.getPoint())) {
									mousePressedInButton = true;
									break;
								}
							}
						}
						if (!mousePressedInButton)
							return;
						mousePressed = true;
						if (visible)
							MarkerLayer.this.mapView.repaint();
					}

					@Override
					public void mouseReleased(MouseEvent ev) {
						if (ev.getButton() != MouseEvent.BUTTON1
								|| !mousePressed)
							return;
						mousePressed = false;
						if (!visible)
							return;
						if (ev.getPoint() != null) {
							for (Marker mkr : data) {
								if (mkr.containsPoint(ev.getPoint()))
									mkr.actionPerformed(new ActionEvent(this,
											0, null));
							}
						}
						MarkerLayer.this.mapView.repaint();
					}
				});
			}
		});
	}

	/**
	 * Return a static icon.
	 */
	@Override
	public Icon getIcon() {
		return ImageProvider.get("layer", "marker_small");
	}

	public void setMapView(MapView mapView) {
		this.mapView = mapView;
	}

	static public Color getColor(String name) {
		return Main.pref.getColor(marktr("gps marker"), name != null ? "layer "
				+ name : null, Color.gray);
	}

	@Override
	public void paint(Graphics g, MapView mv) {
		boolean mousePressedTmp = mousePressed;
		Point mousePos = mv.getMousePosition();
		String mkrTextShow = Main.pref.get("marker.show " + name, "show");

		// g.setColor(getColor(name));
		g.setColor(Color.RED);
		for (Marker mkr : data) {
			Point p = mv.getPoint(mkr.eastNorth);

			// g.drawLine(0, 0, p.x, p.y);
			if (mousePos != null && mkr.containsPoint(mousePos)) {
				mkr.paint(g, mv, mousePressedTmp, mkrTextShow);
				mousePressedTmp = false;
			} else {
				mkr.paint(g, mv, false, mkrTextShow);
			}
		}
	}

	@Override
	public String getToolTipText() {
		return data.size() + " " + trn("marker", "markers", data.size());
	}

	@Override
	public void mergeFrom(Layer from) {
		MarkerLayer layer = (MarkerLayer) from;
		data.addAll(layer.data);
	}

	@Override
	public boolean isMergable(Layer other) {
		return other instanceof MarkerLayer;
	}

	@Override
	public void visitBoundingBox(BoundingXYVisitor v) {
		for (Marker mkr : data)
			v.visit(mkr.eastNorth);
	}

	@Override
	public Object getInfoComponent() {
		return "<html>"
				+ trn("{0} consists of {1} marker",
						"{0} consists of {1} markers", data.size(), name,
						data.size()) + "</html>";
	}

	@Override
	public Component[] getMenuEntries() {
		JMenuItem color = new JMenuItem(tr("Customize Color"),
				ImageProvider.get("colorchooser"));
		color.putClientProperty("help", "Action/LayerCustomizeColor");
		color.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JColorChooser c = new JColorChooser(getColor(name));
				Object[] options = new Object[] { tr("OK"), tr("Cancel"),
						tr("Default") };
				int answer = JOptionPane.showOptionDialog(Main.parent, c,
						tr("Choose a color"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
				switch (answer) {
				case 0:
					Main.pref.putColor("layer " + name, c.getColor());
					break;
				case 1:
					return;
				case 2:
					Main.pref.putColor("layer " + name, null);
					break;
				}
				Main.map.repaint();
			}
		});

		JMenuItem syncaudio = new JMenuItem(tr("Synchronize Audio"),
				ImageProvider.get("audio-sync"));
		syncaudio.putClientProperty("help", "Action/SynchronizeAudio");
		syncaudio.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!AudioPlayer.paused()) {
					JOptionPane
							.showMessageDialog(
									Main.parent,
									tr("You need to pause audio at the moment when you hear your synchronization cue."));
					return;
				}
				AudioMarker recent = AudioMarker.recentlyPlayedMarker();
				if (synchronizeAudioMarkers(recent)) {
					JOptionPane
							.showMessageDialog(
									Main.parent,
									tr("Audio synchronized at point {0}.",
											recent.text));
				} else {
					JOptionPane.showMessageDialog(Main.parent,
							tr("Unable to synchronize in layer being played."));
				}
			}
		});

		JMenuItem moveaudio = new JMenuItem(
				tr("Make Audio Marker at Play Head"),
				ImageProvider.get("addmarkers"));
		moveaudio.putClientProperty("help", "Action/MakeAudioMarkerAtPlayHead");
		moveaudio.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!AudioPlayer.paused()) {
					JOptionPane
							.showMessageDialog(
									Main.parent,
									tr("You need to have paused audio at the point on the track where you want the marker."));
					return;
				}
				PlayHeadMarker playHeadMarker = MarkerLayer.this.mapView.playHeadMarker;
				if (playHeadMarker == null)
					return;
				addAudioMarker(playHeadMarker.time, playHeadMarker.eastNorth);
				MarkerLayer.this.mapView.repaint();
			}
		});

		Collection<Component> components = new ArrayList<Component>();
		components.add(new JMenuItem(new LayerListDialog.ShowHideLayerAction(
				this)));
		components.add(new JMenuItem(new LayerListDialog.ShowHideMarkerText(
				this)));
		components.add(new JMenuItem(
				new LayerListDialog.DeleteLayerAction(this)));
		components.add(new JSeparator());
		components.add(color);
		components.add(new JSeparator());
		components.add(syncaudio);
		if (Main.pref.getBoolean("marker.traceaudio", true)) {
			components.add(moveaudio);
		}
		components.add(new JMenuItem(new RenameLayerAction(getAssociatedFile(),
				this)));
		components.add(new JSeparator());
		components.add(new JMenuItem(new LayerListPopup.InfoAction(this)));
		return components.toArray(new Component[0]);
	}

	public boolean synchronizeAudioMarkers(AudioMarker startMarker) {
		if (startMarker != null && !data.contains(startMarker)) {
			startMarker = null;
		}
		if (startMarker == null) {
			// find the first audioMarker in this layer
			for (Marker m : data) {
				if (m instanceof AudioMarker) {
					startMarker = (AudioMarker) m;
					break;
				}
			}
		}
		if (startMarker == null)
			return false;

		// apply adjustment to all subsequent audio markers in the layer
		double adjustment = AudioPlayer.position() - startMarker.offset; // in
																			// seconds
		boolean seenStart = false;
		URL url = startMarker.url();
		for (Marker m : data) {
			if (m == startMarker)
				seenStart = true;
			if (seenStart) {
				AudioMarker ma = (AudioMarker) m; // it must be an AudioMarker
				if (ma.url().equals(url))
					ma.adjustOffset(adjustment);
			}
		}
		return true;
	}

	public AudioMarker addAudioMarker(double time, EastNorth en) {
		// find first audio marker to get absolute start time
		double offset = 0.0;
		AudioMarker am = null;
		for (Marker m : data) {
			if (m.getClass() == AudioMarker.class) {
				am = (AudioMarker) m;
				offset = time - am.time;
				break;
			}
		}
		if (am == null) {
			JOptionPane
					.showMessageDialog(
							Main.parent,
							tr("No existing audio markers in this layer to offset from."));
			return null;
		}

		// make our new marker
		AudioMarker newAudioMarker = AudioMarker.create(
				Main.proj.eastNorth2latlon(en), AudioMarker.inventName(offset),
				AudioPlayer.url().toString(), this, time, offset);

		// insert it at the right place in a copy the collection
		Collection<Marker> newData = new ArrayList<Marker>();
		am = null;
		AudioMarker ret = newAudioMarker; // save to have return value
		for (Marker m : data) {
			if (m.getClass() == AudioMarker.class) {
				am = (AudioMarker) m;
				if (newAudioMarker != null && offset < am.offset) {
					newAudioMarker.adjustOffset(am.syncOffset()); // i.e. same
																	// as
																	// predecessor
					newData.add(newAudioMarker);
					newAudioMarker = null;
				}
			}
			newData.add(m);
		}

		if (newAudioMarker != null) {
			if (am != null)
				newAudioMarker.adjustOffset(am.syncOffset()); // i.e. same as
																// predecessor
			newData.add(newAudioMarker); // insert at end
		}

		// replace the collection
		data.clear();
		data.addAll(newData);
		return ret;
	}

	public void playAudio() {
		if (Main.map == null || mapView == null)
			return;
		for (Layer layer : mapView.getAllLayers()) {
			if (layer.getClass() == MarkerLayer.class) {
				MarkerLayer markerLayer = (MarkerLayer) layer;
				for (Marker marker : markerLayer.data) {
					if (marker.getClass() == AudioMarker.class) {
						((AudioMarker) marker).play();
						break;
					}
				}
			}
		}
	}

	public void playNextMarker() {
		playAdjacentMarker(true);
	}

	public void playPreviousMarker() {
		playAdjacentMarker(false);
	}

	private void playAdjacentMarker(boolean next) {
		Marker startMarker = AudioMarker.recentlyPlayedMarker();
		if (startMarker == null) {
			// message?
			return;
		}
		Marker previousMarker = null;
		boolean nextTime = false;
		if (Main.map == null || mapView == null)
			return;
		for (Layer layer : mapView.getAllLayers()) {
			if (layer.getClass() == MarkerLayer.class) {
				MarkerLayer markerLayer = (MarkerLayer) layer;
				for (Marker marker : markerLayer.data) {
					if (marker == startMarker) {
						if (next) {
							nextTime = true;
						} else {
							if (previousMarker == null)
								previousMarker = startMarker; // if no previous
																// one, play the
																// first one
																// again
							((AudioMarker) previousMarker).play();
							break;
						}
					} else if (nextTime
							&& marker.getClass() == AudioMarker.class) {
						((AudioMarker) marker).play();
						return;
					}
					if (marker.getClass() == AudioMarker.class)
						previousMarker = marker;
				}
				if (nextTime) {
					// there was no next marker in that layer, so play the last
					// one again
					((AudioMarker) startMarker).play();
					return;
				}
			}
		}
	}

}
