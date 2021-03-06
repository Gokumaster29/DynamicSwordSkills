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

package dynamicswordskills.skills;

import java.util.List;

import dynamicswordskills.api.SkillGroup;
import dynamicswordskills.client.DSSKeyHandler;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModSounds;
import dynamicswordskills.util.PlayerUtils;
import dynamicswordskills.util.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 
 * SWORD BREAK
 * Activation: Double-tap forward then right-click while wielding a weapon
 * Effect: A fierce block that is capable of destroying the opponent's blade
 * Exhaustion: 2.0F - (0.1 * level)
 * Damage: Up to 90 durability damage to the opponent's held item (15 * (level + 1))
 * Knockback: 0.5F + (0.1F * level), slightly better than a standard block
 * Duration: Timing window starts at 4 ticks and increases to 8 by max level
 * Notes:
 * - Only works when being attacked by an enemy holding an item
 * - Has no effect other than blocking the attack if the attacker's held item can not be damaged
 *
 */
public class SwordBreak extends SkillActive
{
	/** Timer during which player is considered actively parrying */
	private int breakTimer;

	/** Counter incremented when next correct key in sequence pressed; reset when activated or if ticksTilFail timer reaches 0 */
	@SideOnly(Side.CLIENT)
	private int keysPressed;

	/** Reset each valid key press until executed; if timer reaches 0, full key sequence must be repeated */
	@SideOnly(Side.CLIENT)
	private int ticksTilFail;

	/** Notification to play miss sound; set to true when activated and false when attack parried */
	private boolean playMissSound;

	public SwordBreak(String translationKey) {
		super(translationKey);
	}

	private SwordBreak(SwordBreak skill) {
		super(skill);
	}

	@Override
	public SwordBreak newInstance() {
		return new SwordBreak(this);
	}

	@Override
	public boolean displayInGroup(SkillGroup group) {
		return super.displayInGroup(group) || group == Skills.WEAPON_GROUP;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(List<String> desc, EntityPlayer player) {
		desc.add(new TextComponentTranslation(getTranslationKey() + ".info.durability", getMaxDamage()).getUnformattedText());
		desc.add(new TextComponentTranslation(getTranslationKey() + ".info.knockback", getKnockbackStrength()).getUnformattedText());
		desc.add(getTimeLimitDisplay(getActiveTime() - getUseDelay()));
		desc.add(getExhaustionDisplay(getExhaustion()));
	}

	@Override
	public boolean isActive() {
		return (breakTimer > 0);
	}

	@Override
	protected float getExhaustion() {
		return 2.0F - (0.1F * level);
	}

	/** Number of ticks that skill will be considered active */
	private int getActiveTime() {
		return 9 + (level / 2);
	}

	/** Number of ticks before player may attempt to use this skill again */
	private int getUseDelay() {
		return (5 - (level / 2));
	}

	/** Maximum amount of damage that may be caused to the opponent's weapon */
	private int getMaxDamage() {
		return (level + 1) * 15;
	}

	/**
	 * Returns the strength of the knockback effect when an attack is parried
	 */
	public float getKnockbackStrength() {
		return 0.5F + (0.1F * level); // 0.5F is the base line per blocking with a shield
	}

	@Override
	public boolean canUse(EntityPlayer player) {
		return super.canUse(player) && !isActive() && PlayerUtils.isWeapon(player.getHeldItemMainhand()) && !player.isHandActive();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean canExecute(EntityPlayer player) {
		return canUse(player) && keysPressed > 1 && ticksTilFail > 0;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isKeyListener(Minecraft mc, KeyBinding key, boolean isLockedOn) {
		if (Config.requiresLockOn() && !isLockedOn) {
			return false;
		}
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean keyPressed(Minecraft mc, KeyBinding key, EntityPlayer player) {
		if ((Config.allowVanillaControls() && key == mc.gameSettings.keyBindForward) || key == DSSKeyHandler.keys[DSSKeyHandler.KEY_FORWARD].getKey()) {
			ticksTilFail = 6;
			if (keysPressed < 2) {
				if (!Config.requiresDoubleTap() && key == DSSKeyHandler.keys[DSSKeyHandler.KEY_FORWARD].getKey()) {
					keysPressed++;
				}
				keysPressed++;
			}
		} else if (key == mc.gameSettings.keyBindUseItem) {
			boolean flag = (canExecute(player) && activate(player));
			ticksTilFail = 0;
			keysPressed = 0;
			return flag;
		} else {
			ticksTilFail = 0;
			keysPressed = 0;
		}
		return false;
	}

	@Override
	protected boolean onActivated(World world, EntityPlayer player) {
		breakTimer = getActiveTime();
		playMissSound = true;
		if (world.isRemote) {
			KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode(), false);
		}
		player.swingArm(EnumHand.MAIN_HAND);
		player.resetCooldown();
		return isActive();
	}

	@Override
	protected void onDeactivated(World world, EntityPlayer player) {
		breakTimer = 0;
	}

	@Override
	public void onUpdate(EntityPlayer player) {
		if (isActive()) {
			if (--breakTimer <= getUseDelay() && playMissSound) {
				playMissSound = false;
				PlayerUtils.playSoundAtEntity(player.getEntityWorld(), player, ModSounds.SWORD_MISS, SoundCategory.PLAYERS, 0.4F, 0.5F);
			}
		} else if (player.getEntityWorld().isRemote && ticksTilFail > 0) {
			--ticksTilFail;
			if (ticksTilFail < 1) {
				keysPressed = 0;
			}
		}
	}

	@Override
	public boolean onBeingAttacked(EntityPlayer player, DamageSource source) {
		if (source.getImmediateSource() instanceof EntityLivingBase) {
			EntityLivingBase attacker = (EntityLivingBase) source.getImmediateSource();
			ItemStack stackToDamage = attacker.getHeldItemMainhand();
			if (breakTimer > getUseDelay() && !stackToDamage.isEmpty() && PlayerUtils.isWeapon(player.getHeldItemMainhand())) {
				breakTimer = getUseDelay(); // only block one attack
				PlayerUtils.playSoundAtEntity(player.getEntityWorld(), player, ModSounds.SWORD_STRIKE, SoundCategory.PLAYERS, 0.4F, 0.5F);
				playMissSound = false;
				if (!player.getEntityWorld().isRemote) {
					int dmg = Math.max(getMaxDamage() / 3, player.getEntityWorld().rand.nextInt(getMaxDamage()));
					stackToDamage.damageItem(dmg, attacker);
					if (stackToDamage.getCount() <= 0) {
						PlayerUtils.playSoundAtEntity(attacker.getEntityWorld(), attacker, SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 0.8F, 0.8F + player.getEntityWorld().rand.nextFloat() * 0.4F);
						attacker.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
					}
				}
				TargetUtils.knockTargetBack(attacker, player, getKnockbackStrength());
				return true;
			} // don't deactivate early, as there is a delay between uses
		}
		return false;
	}
}
