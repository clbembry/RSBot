import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;
import java.util.HashSet;
import java.util.Set;

import org.rsbot.event.events.MessageEvent;
import org.rsbot.event.listeners.MessageListener;
import org.rsbot.event.listeners.PaintListener;
import org.rsbot.script.Script;
import org.rsbot.script.ScriptManifest;
import org.rsbot.script.methods.Bank;
import org.rsbot.script.methods.Game;
import org.rsbot.script.methods.Skills;
import org.rsbot.script.util.Filter;
import org.rsbot.script.util.Timer;
import org.rsbot.script.wrappers.RSArea;
import org.rsbot.script.wrappers.RSItem;
import org.rsbot.script.wrappers.RSModel;
import org.rsbot.script.wrappers.RSNPC;
import org.rsbot.script.wrappers.RSObject;
import org.rsbot.script.wrappers.RSPath;
import org.rsbot.script.wrappers.RSTile;
import org.rsbot.script.wrappers.RSTilePath;

@ScriptManifest(authors = "Aion", name = "Aion's Air Crafter", version = 0.2, description = "Either wear an air tiara or have an air talisman in your inventory.")
public class AionAirCrafter extends Script implements PaintListener,
		MessageListener {

	public static interface Constants {

		int ITEM_AIR_RUNE = 556;

		int ITEM_AIR_TALISMAN = 1438;

		int ITEM_RUNE_ESS = 1436;

		int OBJECT_ALTAR = 2478;

		int OBJECT_PORTAL = 2465;

		int OBJECT_RUINS = 2452;

		int NPC_BANKER = 5912;

		double VERSION = AionAirCrafter.class.getAnnotation(
				ScriptManifest.class).version();

		Filter<RSNPC> FILTER_NPC = new Filter<RSNPC>() {
			@Override
			public boolean accept(RSNPC t) {
				return t != null && t.getID() == Constants.NPC_BANKER;
			}
		};

		RSTile[] PATH = { new RSTile(3185, 3434), new RSTile(3173, 3428),
				new RSTile(3159, 3423), new RSTile(3147, 3415),
				new RSTile(3135, 3407), new RSTile(3129, 3405) };

		RSArea AREA_ALTAR = new RSArea(new RSTile(2835, 4823), new RSTile(2851,
				4843));

		RSArea AREA_BANK = new RSArea(new RSTile(3178, 3431), new RSTile(3195,
				3447));

		RSArea AREA_RUINS = new RSArea(new RSTile(3122, 3401), new RSTile(3132,
				3409));

		RSArea AREA_MUSICIAN = new RSArea(new RSTile(3149, 3419), new RSTile(
				3157, 3424));
	}

	public static abstract class Action {

		public abstract String getDesc();

		public abstract boolean isValid();

		public abstract void process();
	}

	public class BankAction extends Action {

		@Override
		public String getDesc() {
			if (!bank.isOpen()) {
				return "Opening bank.";
			} else if (inventory.contains(Constants.ITEM_AIR_RUNE)) {
				return "Depositing items.";
			}
			return "Withdrawing items.";
		}

		@Override
		public boolean isValid() {
			return !canCraft() && inBank();
		}

		@Override
		public void process() {
			if (bank.isOpen()) {
				if (inventory.contains(Constants.ITEM_AIR_RUNE)) {
					if (inventory.containsOneOf(Constants.ITEM_AIR_TALISMAN,
							Constants.ITEM_RUNE_ESS)) {
						bank.depositAllExcept(Constants.ITEM_AIR_TALISMAN,
								Constants.ITEM_RUNE_ESS);
					} else {
						bank.depositAll();
					}
				} else if (!inventory.contains(Constants.ITEM_RUNE_ESS)) {
					int runeCount = bank.getCount(Constants.ITEM_RUNE_ESS);
					if (runeCount < 1) {
						log("You are out of rune essences!");
					} else {
						bank.withdraw(Constants.ITEM_RUNE_ESS, 0);
					}
				}
				sleep(600, 800);
			} else {
				RSNPC banker = npcs.getNearest(Constants.FILTER_NPC);
				if (banker != null) {
					if (banker.isOnScreen()) {
						banker.doAction("Bank");
						if (calc.distanceTo(banker) > 1) {
							waitToMove(random(600, 900));
							waitToStop();
						}
						waitForIface(Bank.INTERFACE_BANK, random(600, 800));
					} else {
						camera.turnTo(banker);
						if (!banker.isOnScreen()) {
							walking.getPath(banker.getLocation()).traverse();
						}
					}
				}
			}
		}
	}

	public abstract class ObjectAction extends Action {

		private final String action;
		private final int id;

		public ObjectAction(String action, int id) {
			this.action = action;
			this.id = id;
		}

		@Override
		public String getDesc() {
			return "Interacting with object.";
		}

		@Override
		public void process() {
			RSObject object = objects.getNearest(id);
			if (object != null) {
				if (object.isOnScreen()) {
					object.doAction(action);
					if (calc.distanceTo(object) > 1) {
						waitToMove(random(600, 900));
					}
				} else {
					camera.turnTo(object);
					if (!object.isOnScreen()) {
						walking.getPath(object.getLocation()).traverse();
					}
				}
			}
		}
	}

	public abstract class WalkToArea extends Action {

		private final RSPath path;

		public WalkToArea(RSPath path) {
			this.path = path;
		}

		protected abstract boolean canRest();

		protected abstract boolean isTargetValid();

		@Override
		public boolean isValid() {
			return isTargetValid();
		}

		@Override
		public void process() {
			if (walking.getEnergy() < 20) {
				if (canRest() && inMusician()) {
					RSTilePath tilePath = walking
							.newTilePath(Constants.AREA_MUSICIAN.getTileArray());
					if (!tilePath.traverse()) {
						walking.newTilePath(
								new RSTile[] { Constants.AREA_MUSICIAN
										.getCentralTile() }).traverse();
					}
					waitToMove(random(600, 900));
					walking.rest(100);
				}
			} else if (!walking.isRunEnabled()) {
				walking.setRun(true);
			}
			path.traverse();
		}
	}

	private int runesCrafted;

	private int startExp;
	private int startLvl;

	private Set<Action> actions;
	private Action action;

	private Timer timer;

	@Override
	public boolean onStart() {
		timer = new Timer(0);

		actions = new HashSet<Action>();

		actions.add(new BankAction());

		actions.add(new ObjectAction("Enter", Constants.OBJECT_RUINS) {

			@Override
			public String getDesc() {
				return "Entering mysterious ruins.";
			}

			@Override
			public boolean isValid() {
				return canCraft() && inRuins();
			}

			@Override
			public void process() {
				if (inventory.contains(Constants.ITEM_AIR_TALISMAN)) {
					if (!inventory.isItemSelected()) {
						inventory.getItem(Constants.ITEM_AIR_TALISMAN).doClick(
								true);
					} else {
						RSItem selItem = inventory.getSelectedItem();
						if (selItem.getID() != Constants.ITEM_AIR_TALISMAN) {
							selItem.doClick(true);
							return;
						}
					}
					RSObject obj = objects.getNearest(Constants.OBJECT_ALTAR);
					if (obj != null) {
						Point toClick = getScreenPoint(obj);
						if (toClick.x != -1 && toClick.y != -1) {
							mouse.click(toClick, true);
							if (calc.distanceTo(obj) > 1) {
								waitToMove(random(600, 900));
							}
							return;
						} else if (obj.isOnScreen()) {
							obj.doClick(true);
							if (calc.distanceTo(obj) > 1) {
								waitToMove(random(600, 900));
							}
						} else {
							camera.turnTo(obj);
							if (!obj.isOnScreen())
								walking.getPath(obj.getLocation()).traverse();
						}
					}
				} else {
					super.process();
				}
				waitToStop();
			}
		});

		actions.add(new ObjectAction("Craft-rune", Constants.OBJECT_ALTAR) {

			@Override
			public String getDesc() {
				return "Crafting runes.";
			}

			@Override
			public boolean isValid() {
				return canCraft() && inAltar();
			}

			@Override
			public void process() {
				super.process();
				sleep(200, 400);
				waitForAnim(random(900, 1200));
				if (random(1, 6) == 3) {
					RSObject portal = objects
							.getNearest(Constants.OBJECT_PORTAL);
					if (portal != null) {
						Point toClick = getScreenPoint(portal);
						if (toClick.x != -1 && toClick.y != -1) {
							mouse.click(toClick, true);
						} else {
							portal.doClick(false);
						}
						if (menu.isOpen()) {
							if (!menu.contains("Enter")) {
								while (menu.isOpen()) {
									mouse.moveRandomly(750);
								}
							}
						}
					}
				}
			}
		});

		actions.add(new ObjectAction("Enter", Constants.OBJECT_PORTAL) {

			@Override
			public String getDesc() {
				return "Leaving mysterious ruins";
			}

			@Override
			public boolean isValid() {
				return !canCraft() && inAltar();
			}

			@Override
			public void process() {
				if (menu.isOpen()) {
					if (menu.contains("Enter")) {
						menu.doAction("Enter");
						waitToMove(random(600, 900));
					} else {
						mouse.moveSlightly();
					}
				} else {
					super.process();
				}
				waitToStop();
			}
		});

		actions.add(new WalkToArea(walking.newTilePath(Constants.PATH)
				.reverse()) {

			@Override
			protected boolean canRest() {
				return true;
			}

			@Override
			protected boolean isTargetValid() {
				return !canCraft() && !inAltar() && !inBank();
			}

			@Override
			public String getDesc() {
				return "Heading to bank";
			}
		});

		actions.add(new WalkToArea(walking.newTilePath(Constants.PATH)) {

			@Override
			protected boolean canRest() {
				return true;
			}

			@Override
			protected boolean isTargetValid() {
				return canCraft() && !inRuins() && !inAltar();
			}

			@Override
			public String getDesc() {
				return "Heading to mysterious ruins";
			}
		});

		startExp = skills.getCurrentExp(Skills.RUNECRAFTING);
		startLvl = skills.getCurrentLevel(Skills.RUNECRAFTING);
		return true;
	}

	@Override
	public int loop() {
		if (interfaces.get(741).isValid()) {
			interfaces.getComponent(741, 9).doClick();
			return random(300, 500);
		} else if (interfaces.get(740).isValid()) {
			env.saveScreenshot(false);
			interfaces.getComponent(740, 3).doClick();
			sleep(600, 700);
			if (game.getCurrentTab() != Game.TAB_STATS) {
				game.openTab(Game.TAB_STATS);
				sleep(800, 1200);
			}
			skills.doHover(Skills.INTERFACE_RUNECRAFTING);
			sleep(150, 300);
			mouse.click(true);
			return random(random(1000, 1300), 2000);
		}

		if (action != null) {
			if (action.isValid()) {
				action.process();
			} else {
				action = null;
			}
		} else {
			for (Action a : actions) {
				if (a.isValid()) {
					action = a;
					break;
				}
			}
		}
		if (random(1, 10) == random(1, 6)) {
			antiban();
		}
		return random(200, 400);
	}

	@Override
	public void onRepaint(Graphics g) {
		if (game.isLoggedIn() && !game.isLoginScreen()) {
			g.setColor(new Color(16, 16, 16, 123));
			g.fillRoundRect(8, 179, 243, 153, 15, 15);

			g.setColor(Color.RED);
			g.draw3DRect(13, 291, 231, 15, true);

			g.setColor(new Color(48, 225, 48, 170));
			g.fill3DRect(14, 292, (getPercentToLvl() * 229 / 100), 14, true);

			g.setColor(Color.WHITE);
			g.drawString("Aion's Air Crafter v" + getVersion(), 65, 192);
			g.drawString("Runtime: " + getRuntime(), 13, 206);

			g.drawString("Crafted " + format(getRunesCrafted()) + " air runes",
					13, 225);
			g.drawString("Gained " + format(getExpGained()) + " exp", 13, 239);
			g.drawString("Runes/Hour: " + format((int) getRunesHour()), 140,
					225);
			g.drawString("Exp/Hour: " + format((int) getExpHour()), 140, 239);

			int lvlGained = getLvlGained();
			String text = lvlGained == 0 ? "runecrafting" : "";
			g.drawString("Current " + text + " level: " + getRunecraftLvl(),
					13, 258);
			if (lvlGained != 0) {
				text = lvlGained == 1 ? "" : "s";
				g.drawString("Gained " + lvlGained + " level" + text, 140, 258);
			}

			g.drawString("Exp left: " + format(getExpToLvl()), 13, 272);
			g.drawString("Runes left: " + format(getRunesLeft()), 140, 272);
			g.drawString("Estimated time to level: " + getTimeToLvl(), 13, 286);
			g.drawString(getPercentToLvl() + "%", 113, 303);

			if (action != null) {
				g.drawString("Status: " + action.getDesc(), 13, 325);
			}

		}

		Point mPoint = mouse.getLocation();
		Point pPoint = mouse.getPressLocation();
		long mpt = System.currentTimeMillis() - mouse.getPressTime();

		if (mpt < 1000) {
			g.setColor(Color.red);
			g.drawOval(pPoint.x - 2, pPoint.y - 2, 4, 4);
			g.drawOval(pPoint.x - 9, pPoint.y - 9, 18, 18);
		}

		g.setColor(Color.YELLOW);
		g.drawOval(mPoint.x - 2, mPoint.y - 2, 4, 4);
		g.drawOval(mPoint.x - 9, mPoint.y - 9, 18, 18);
	}

	@Override
	public void messageReceived(MessageEvent e) {
		String message = e.getMessage().toLowerCase();
		if (message.contains("bind the temple")) {
			runesCrafted += inventory.getCount(Constants.ITEM_RUNE_ESS);
		} else if (message.contains("you've just")) {
			log(message);
		}
	}

	private void antiban() {
		switch (random(1, 50)) {
		case 2:
			if (random(1, 5) != 1) {
				break;
			}
			mouse.moveSlightly();
			break;
		case 6:
			if (random(1, 18) != 7) {
				break;
			}
			if (game.getCurrentTab() != Game.TAB_STATS) {
				game.openTab(Game.TAB_STATS);
				sleep(500, 900);
			}
			skills.doHover(Skills.INTERFACE_RUNECRAFTING);
			sleep(random(1400, 2000), 3000);
			if (random(0, 5) != 3) {
				break;
			}
			mouse.moveSlightly();
			break;
		case 9:
		case 14:
		case 17:
		case 25:
			camera.setAngle(random(-360, 360));
			break;
		case 30:
		case 34:
		case 37:
		case 40:
			camera.setPitch(camera.getPitch() >= random(65, 101) ? random(0, 61)
					: random(61, 101));
		}
	}

	private boolean canCraft() {
		return inventory.contains(Constants.ITEM_RUNE_ESS);
	}

	private String format(int number) {
		return format(String.valueOf(number));
	}

	private String format(String number) {
		if (number.length() < 4) {
			return number;
		}
		return format(number.substring(0, number.length() - 3)) + ","
				+ number.substring(number.length() - 3, number.length());
	}

	private int getExpGained() {
		if (startExp == -1) {
			if (game.getClientState() == 10) {
				startExp = skills.getCurrentExp(Skills.RUNECRAFTING);
			}
		}
		return skills.getCurrentExp(Skills.RUNECRAFTING) - startExp;
	}

	private double getExpHour() {
		int xpGained = getExpGained();
		long start = System.currentTimeMillis() - timer.getElapsed();
		return xpGained * 3600000D / (System.currentTimeMillis() - start);
	}

	private int getExpToLvl() {
		return skills.getExpToNextLevel(Skills.RUNECRAFTING);
	}

	private int getLvlGained() {
		if (startLvl == -1) {
			if (game.getClientState() == 10) {
				startLvl = skills.getCurrentLevel(Skills.RUNECRAFTING);
			}
		}
		return skills.getCurrentLevel(Skills.RUNECRAFTING) - startLvl;
	}

	private int getPercentToLvl() {
		return skills.getPercentToNextLevel(Skills.RUNECRAFTING);
	}

	private int getRunecraftLvl() {
		return skills.getCurrentLevel(Skills.RUNECRAFTING);
	}

	private int getRunesCrafted() {
		return runesCrafted;
	}

	private double getRunesHour() {
		int runes = getRunesCrafted();
		long start = System.currentTimeMillis() - timer.getElapsed();
		return runes * 3600000D / (System.currentTimeMillis() - start);
	}

	private int getRunesLeft() {
		return getExpToLvl() / 5;
	}

	private String getRuntime() {
		return timer.toElapsedString();
	}

	private Point getScreenPoint(RSObject obj) {
		if (obj != null) {
			RSModel model = obj.getModel();
			if (model != null) {
				for (Polygon pol : model.getTriangles()) {
					for (int i = 0; i < pol.npoints; i++) {
						Point p = new Point(pol.xpoints[i], pol.ypoints[i]);
						if (calc.pointOnScreen(p)) {
							return p;
						}
					}
				}
			}
		}
		return new Point(-1, -1);
	}

	private String getTimeFormat(long time) {
		return Timer.format(time);
	}

	private String getTimeToLvl() {
		return getTimeFormat((long) (getExpToLvl() / getExpHour() * 3600000D));
	}

	private double getVersion() {
		return Constants.VERSION;
	}

	private boolean inAltar() {
		return Constants.AREA_ALTAR.contains(getMyPlayer().getLocation());
	}

	private boolean inBank() {
		return Constants.AREA_BANK.contains(getMyPlayer().getLocation());
	}

	private boolean inRuins() {
		return Constants.AREA_RUINS.contains(getMyPlayer().getLocation());
	}

	private boolean inMusician() {
		return Constants.AREA_MUSICIAN.contains(getMyPlayer().getLocation());
	}

	private void waitForAnim(int timeout) {
		long endTime = System.currentTimeMillis() + timeout;
		while (System.currentTimeMillis() < endTime) {
			if (getMyPlayer().getAnimation() != -1) {
				break;
			}
			sleep(5, 15);
		}
	}

	private void waitForIface(int iface, int timeout) {
		long endTime = System.currentTimeMillis() + timeout;
		while (System.currentTimeMillis() < endTime) {
			if (interfaces.get(iface).isValid()) {
				break;
			}
			sleep(5, 15);
		}
	}

	private boolean waitToMove(int timeout) {
		long endTime = System.currentTimeMillis() + timeout;
		while (System.currentTimeMillis() < endTime) {
			if (getMyPlayer().isMoving()) {
				return true;
			}
			sleep(5, 15);
		}
		return false;
	}

	private void waitToStop() {
		do {
			sleep(5, 15);
		} while (getMyPlayer().isMoving());
	}
}