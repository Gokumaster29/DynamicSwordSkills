/**
    Copyright (C) <2017> <coolAlias>

    This file is part of coolAlias' Dynamic Sword Skills Minecraft Mod; as such,
    you can redistribute it and/or modify it under the terms of the GNU
    General Public License as published by the Free Software Foundation,
    either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package dynamicswordskills.client;

import java.util.ArrayList;
import java.util.List;

import dynamicswordskills.DSSCombatEvents;
import dynamicswordskills.client.gui.ComboOverlay;
import dynamicswordskills.client.gui.GuiEndingBlowOverlay;
import dynamicswordskills.client.gui.IGuiOverlay;
import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.skills.ICombo;
import dynamicswordskills.skills.ILockOnTarget;
import dynamicswordskills.util.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 
 * Handles all client-sided events, such as render events, mouse event, etc.
 *
 */
@SideOnly(Side.CLIENT)
public class DSSClientEvents
{
	private final Minecraft mc;

	/** List of all GUI Overlays that may need rendering */
	private final List<IGuiOverlay> overlays = new ArrayList<IGuiOverlay>();

	/** List of GUI overlays that have rendered this tick */
	private final List<IGuiOverlay> rendered = new ArrayList<IGuiOverlay>();

	public DSSClientEvents() {
		this.mc = Minecraft.getMinecraft();
		overlays.add(new ComboOverlay(mc));
		overlays.add(new GuiEndingBlowOverlay(mc));
	}

	@SubscribeEvent
	public void onRenderExperienceBar(RenderGameOverlayEvent.Post event) {
		if (event.getType() != RenderGameOverlayEvent.ElementType.EXPERIENCE) {
			return;
		}
		for (IGuiOverlay overlay : this.overlays) {
			if (overlay.shouldRender() && overlay.renderOverlay(event.getResolution(), this.rendered)) {
				this.rendered.add(overlay);
			}
		}
		this.rendered.clear();
	}

	/**
	 * Attacks current target if player not currently using an item and {@link ICombo#onAttack}
	 * doesn't return false (i.e. doesn't miss)
	 * @param skill must implement BOTH {@link ILockOnTarget} AND {@link ICombo}
	 */
	public static void performComboAttack(Minecraft mc, ILockOnTarget skill) {
		if (!mc.thePlayer.isHandActive()) {
			if (skill instanceof ICombo && ((ICombo) skill).onAttack(mc.thePlayer)) {
				Entity entity = TargetUtils.getMouseOverEntity();
				mc.playerController.attackEntity(mc.thePlayer, (entity != null ? entity : skill.getCurrentTarget()));
			}
			DSSCombatEvents.setPlayerAttackTime(mc.thePlayer);
			mc.thePlayer.swingArm(EnumHand.MAIN_HAND);
			mc.thePlayer.resetCooldown();
		}
	}

	/**
	 * Passes mouse key presses and releases to the appropriate DSSKeyHandler method
	 * for skill usage and possibly event cancellation
	 */
	@SubscribeEvent
	public void onMouseChanged(MouseEvent event) {
		if ((event.getButton() == -1 && event.getDwheel() == 0)) {
			return;
		} else if (event.getDwheel() != 0) {
			// Cancel mouse wheel while animations are in progress
			event.setCanceled(!DSSPlayerInfo.get(mc.thePlayer).canInteract());
		} else {
			// Corresponding KeyBinding key codes for mouse buttons are (event.button - 100)
			int mouseKey = event.getButton() - 100;
			if (event.isButtonstate()) {
				event.setCanceled(DSSKeyHandler.onKeyPressed(mc, mouseKey));
			} else {
				DSSKeyHandler.onKeyReleased(mc, mouseKey);
			}
		}
	}
}
