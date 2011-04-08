package org.rsbot.script.wrappers;

import org.rsbot.script.methods.MethodContext;
import org.rsbot.util.GlobalConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * The web generation and wrapper control.
 *
 * @author Timer
 */

public class Web extends WebSkeleton {

	/**
	 * The end tile.
	 */
	private final RSTile from, to;

	/**
	 * The WebPath variable.
	 */
	public WebPath path = null;

	/**
	 * The WebMap allocation.
	 */
	private WebMap map = null;

	/**
	 * @param ctx The MethodContext.
	 * @param end The end tile you wish to result at.
	 */
	public Web(final MethodContext ctx, final RSTile start, final RSTile end) {
		super(ctx);
		this.from = start;
		this.to = end;
		getPath();
	}

	/**
	 * Returns if the map needs set.
	 *
	 * @return <tt>true</tt> if the map needs set, otherwise false.
	 */
	public boolean mapNeedsSet() {
		return map == null;
	}

	private static String substringBetween(String str, String open, String close) {
		if (str == null || open == null || close == null) {
			return null;
		}
		int start = str.indexOf(open);
		if (start != -1) {
			int end = str.indexOf(close, start + open.length());
			if (end != -1) {
				return str.substring(start + open.length(), end);
			}
		}
		return null;
	}

	/**
	 * Sets the map up.
	 */
	public void setMap() {
		File mapData = new File(GlobalConfiguration.Paths.getWebCache());
		if (mapData.exists() && mapData.canRead()) {
			final int xOff = 2044;
			final int yOff = 4168;
			HashMap<Integer, RSTile> nodes = new HashMap<Integer, RSTile>();
			List<Point> edges = new ArrayList<Point>();
			try {
				File file = new File(GlobalConfiguration.Paths.getWebCache());
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc = db.parse(file);
				doc.getDocumentElement().normalize();
				NodeList nodeLst = doc.getElementsByTagName("Node");
				for (int s = 0; s < nodeLst.getLength(); s++) {
					Node NodeIndex = nodeLst.item(s);
					if (NodeIndex.getNodeType() == Node.ELEMENT_NODE) {
						Element MasterNodeElement = (Element) NodeIndex;

						NodeList NodeListX = MasterNodeElement.getElementsByTagName("x");
						Element NodeElementX = (Element) NodeListX.item(0);
						NodeList NodeX = NodeElementX.getChildNodes();
						int x = Integer.parseInt(((Node) NodeX.item(0)).getNodeValue());

						NodeList NodeListY = MasterNodeElement.getElementsByTagName("y");
						Element NodeElementY = (Element) NodeListY.item(0);
						NodeList NodeY = NodeElementY.getChildNodes();
						int y = Integer.parseInt(((Node) NodeY.item(0)).getNodeValue());

						nodes.put(s, new RSTile(xOff + x, yOff - y));
					}
				}
				NodeList edgeList = doc.getElementsByTagName("Edge");
				for (int s = 0; s < edgeList.getLength(); s++) {
					Node NodeIndex = edgeList.item(s);
					if (NodeIndex.getNodeType() == Node.ELEMENT_NODE) {
						Element MasterNodeElement = (Element) NodeIndex;

						NodeList NodeListX = MasterNodeElement.getElementsByTagName("a");
						Element NodeElementX = (Element) NodeListX.item(0);
						NodeList NodeX = NodeElementX.getChildNodes();
						int x = Integer.parseInt(((Node) NodeX.item(0)).getNodeValue());

						NodeList NodeListY = MasterNodeElement.getElementsByTagName("b");
						Element NodeElementY = (Element) NodeListY.item(0);
						NodeList NodeY = NodeElementY.getChildNodes();
						int y = Integer.parseInt(((Node) NodeY.item(0)).getNodeValue());

						edges.add(new Point(x, y));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			Set<Integer> keySet = nodes.keySet();
			Iterator<Integer> keys = keySet.iterator();
			List<WebTile> webTiles = new ArrayList<WebTile>();
			while (keys.hasNext()) {
				int key = keys.next();
				RSTile tile = nodes.get(key);
				List<Integer> surrounding = new ArrayList<Integer>();
				Iterator<Point> edgeIterator = edges.listIterator();
				while (edgeIterator.hasNext()) {
					Point currentEdge = edgeIterator.next();
					if (currentEdge.x == key) {
						surrounding.add(currentEdge.y);
					} else if (currentEdge.y == key) {
						surrounding.add(currentEdge.x);
					}
				}
				int[] surrounding_arr = new int[surrounding.size()];
				for (int f = 0; f < surrounding_arr.length; f++) {
					surrounding_arr[f] = surrounding.get(f).intValue();
				}
				WebTile temp = new WebTile(tile, surrounding_arr, null);
				webTiles.add(temp);
			}
			map = new WebMap(webTiles.toArray(new WebTile[webTiles.size()]));
			return;
		}
		map = null;
	}

	/**
	 * Traverses on the web path.
	 *
	 * @param options The flags to take into account while traversing the path.
	 * @return <tt>true</tt> if walked, otherwise false.
	 */
	public boolean traverse(EnumSet<TraversalOption> options) {
		return path != null ? path.traverse(options) : false;
	}

	/**
	 * Gets the path between two tiles.
	 *
	 * @return The web tile path.
	 */
	public void getPath() {
		try {
			if (mapNeedsSet()) {
				setMap();
			}
			if (from == null || to == null || map == null) {
				path = null;
				return;
			}
			WebTile start = map.getWebTile(from);
			WebTile end = map.getWebTile(to);
			if (start == null || end == null) {
				path = null;
				return;
			}
			if (start.equals(end)) {
				path = new WebPath(methods, new WebTile[]{end});
				return;
			}
			HashSet<WebTile> open = new HashSet<WebTile>();
			HashSet<WebTile> closed = new HashSet<WebTile>();
			WebTile curr = start;
			WebTile dest = end;
			curr.f = map.heuristic(curr, dest);
			open.add(curr);
			while (!open.isEmpty()) {
				curr = lowest_f(open);
				if (curr.equals(dest)) {
					path = new WebPath(methods, path(curr));
					return;
				}
				open.remove(curr);
				closed.add(curr);
				for (int iNext : curr.connectingIndex()) {
					WebTile next = map.getWebTile(iNext);
					if (next.req != null) {
						if (!next.req.canDo()) {
							continue;
						}
					}
					if (!closed.contains(next)) {
						double t = curr.g + map.dist(curr, next);
						boolean use_t = false;
						if (!open.contains(next)) {
							open.add(next);
							use_t = true;
						} else if (t < next.g) {
							use_t = true;
						}
						if (use_t) {
							next.parent = curr;
							next.g = t;
							next.f = t + map.heuristic(next, dest);
						}
					}
				}
			}
			path = null;
			return;
		} catch (Exception e) {
			e.printStackTrace();
			path = null;
			return;
		}
	}

	/**
	 * Gets the lowest f score.
	 *
	 * @param open The set of web tiles.
	 * @return The lowest f score tile.
	 */
	private WebTile lowest_f(Set<WebTile> open) {
		WebTile best = null;
		for (WebTile t : open) {
			if (best == null || t.f < best.f) {
				best = t;
			}
		}
		return best;
	}

	/**
	 * Returns the array of tiles in the web.
	 *
	 * @return The array of tiles.
	 */
	private RSTile[] path() {
		return path.getTiles();
	}

	/**
	 * Reconstructs the path.
	 *
	 * @param end The final web tile.
	 * @return The path.
	 */
	private WebTile[] path(WebTile end) {
		LinkedList<RSTile> path = new LinkedList<RSTile>();
		WebTile p = end;
		while (p != null) {
			path.addFirst(p);
			p = p.parent;
		}
		return path.toArray(new WebTile[path.size()]);
	}

	/**
	 * Get next tile.
	 *
	 * @return The next tile in the skeleton.
	 */
	public RSTile getNext() {
		RSTile finalTile = null;
		for (RSTile tile : path()) {
			if (methods.calc.tileOnMap(tile)) {
				finalTile = tile;
			}
		}
		return finalTile;
	}

	/**
	 * Gets the next start tile.
	 *
	 * @return The start tile.
	 */
	public RSTile getStart() {
		return path() != null && path().length > 0 ? path()[0] : null;
	}

	/**
	 * The end tile.
	 *
	 * @return The end tile.
	 */
	public RSTile getEnd() {
		RSTile[] path = path();
		return path != null && path.length > 0 ? path[path.length - 1] : null;
	}

	/**
	 * Returns if you're at your destination.
	 *
	 * @return <tt>true</tt> if at destination, otherwise false.
	 */
	public boolean atDestination() {
		return getEnd() != null ? methods.calc.distanceTo(getEnd()) < 8 : false;
	}

}