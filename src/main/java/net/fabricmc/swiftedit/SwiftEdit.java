package net.fabricmc.swiftedit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.debug.DebugRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;

import java.util.Objects;

public class SwiftEdit implements ClientModInitializer {

	public static final MinecraftClient client = MinecraftClient.getInstance();
	public static final Logger LOGGER = LoggerFactory.getLogger("modid");
	public static final TextRenderer textRenderer = client.textRenderer;

	public static boolean isActivated = false;
	public static boolean isSurfaceMode = false; // Surface Mode = starts region from the adjacent block
	public static int regionSetMode = 0; // 0=none, 1=planar, 2=height, 3=done
	public static int editMode = 0; // 0=set, 1=stack, 2=copy
	public static BlockPos pos1;
	public static BlockPos pos2;

	// KEY BINDS
	public static KeyBinding kToggleQE;
	public static boolean onKToggleQE = true;
	public static KeyBinding kToggleSurface;
	public static boolean onKToggleSurface = true;
	public static KeyBinding kLeftClick = client.options.attackKey;
	public static boolean onKLeftClick = true;
	public static KeyBinding kRightClick = client.options.useKey;
	public static boolean onKRightClick = true;


	@Override
	public void onInitializeClient() {
		ClearAll();

		// REGISTER KEYBIND
		kToggleQE = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.swift_edit.toggle", GLFW.GLFW_KEY_LEFT_ALT, "key.categories.swift_edit")
		);
		kToggleSurface = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.swift_edit.surface_mode", GLFW.GLFW_KEY_CAPS_LOCK, "key.categories.swift_edit")
		);


		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// END CLIENT TICK
			if(onKToggleQE && kToggleQE.isPressed()){
				if(isActivated)ClearAll();
				else Activate();
				onKToggleQE = false;
			}else if(!kToggleQE.isPressed()) onKToggleQE = true;

			if(isActivated){
				if(onKLeftClick && kLeftClick.isPressed()){
					switch (regionSetMode){
						case 0:
							// click on nothing
							pos1 = GetAimingPosition();
							if(pos1 != null){
								Command("//pos1 "+pos1.getX()+","+pos1.getY()+","+pos1.getZ());
								regionSetMode = 1;
							}
							break;
						case 1:
							// has set first position
							pos2 = GetPlanarPosition(pos1.getY());
							regionSetMode =2;
							break;
						case 2:
							// has set planar area
							pos2 = GetHeight(pos2);
							Command("//pos2 "+pos2.getX()+","+pos2.getY()+","+pos2.getZ());
							break;
						case 3:
							// has set region
							Command("//set 0");
							ClearRegion();
							break;
					}
					onKLeftClick = false;
				} else if (!kLeftClick.isPressed()) onKLeftClick = true;

				if (onKRightClick && kRightClick.isPressed()){
					if(regionSetMode == 1 || regionSetMode == 2){
						ClearRegion();
					} else if (regionSetMode == 3) {
						Command("//set hand");
						ClearRegion();
					}
					onKRightClick = false;
				} else if (!kRightClick.isPressed()) onKRightClick = true;

				if (onKToggleSurface && kToggleSurface.isPressed()){
					isSurfaceMode = !isSurfaceMode;
					onKToggleSurface = false;
				} else if(!kToggleSurface.isPressed()) onKToggleSurface = true;

			}

		});

		WorldRenderEvents.BEFORE_DEBUG_RENDER.register((context) -> {
			// RENDER DEBUG
			if(isActivated){
				switch (regionSetMode){
					case 0:
						DebugRenderer.drawBox(GetAimingPosition(),1F,1,0,0,0.1f);
						break;
					case 1:
						DebugRenderer.drawBox(pos1,GetPlanarPosition(pos1.getY()),0,1,0,0.1f);
						break;
					case 2:
						DebugRenderer.drawBox(pos1,GetHeight(pos2),0,0,1,0.1f);
						break;
					case 3:
						break;
				}
			}

		});

		// Register a callback to render custom HUD elements
		HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> {
			renderCustomText(matrixStack);
		});

	}

	public BlockPos GetAimingPosition(){
		BlockHitResult raycastResult = (BlockHitResult) client.crosshairTarget;

		if (raycastResult != null && raycastResult.getType() == HitResult.Type.BLOCK) {
			BlockPos blockPos = raycastResult.getBlockPos();

			if (isSurfaceMode) {
				blockPos = blockPos.offset(raycastResult.getSide());
			}

			return blockPos;
		}
		
		return null;
	}

	public BlockPos GetPlanarPosition(int yPos){
		Vec3d cameraPos = Objects.requireNonNull(client.getCameraEntity()).getPos();
		Vec3d cameraView = client.getCameraEntity().getRotationVec(1.0F);

		// Calculate the intersection point with the specified Y-plane.
		double t = (yPos - cameraPos.y) / cameraView.y;
		double blockX = cameraPos.x + t * cameraView.x;
		double blockZ = cameraPos.z + t * cameraView.z;

		return new BlockPos(blockX, yPos, blockZ);
	}

	public BlockPos GetHeight(BlockPos pPos){
		Vec3d viewRot = client.getCameraEntity().getRotationVec(1.0F);
		Vec3d pPosd = new Vec3d(pPos.getX(), pPos.getY(), pPos.getZ());
		double distance = client.player.getPos().distanceTo(new Vec3d(pPosd.x,client.player.getPos().y, pPosd.getZ()));
		int diff = (int)(viewRot.multiply(distance/viewRot.distanceTo(new Vec3d(0,viewRot.y,0))).y-pPosd.y);
		return pPos.withY(pPos.getY()+diff);
	}

	public static void Command(String command) {
		if (client != null && client.player != null) {
			client.player.sendChatMessage(command);
		}
	}

	public void Activate(){
		isActivated = true;
	}

	public void ClearRegion(){
		Command("//desel");
		pos1 = null;
		pos2 = null;
		regionSetMode = 0;
		onKLeftClick = true;
		onKRightClick = true;
	}

	public void ClearAll(){
		ClearRegion();
		isActivated = false;
	}

	private void renderCustomText(MatrixStack matrixStack) {
		// Set the position where you want to render the text
		int x = 10; // X-coordinate
		int y = 10; // Y-coordinate

		// The text you want to display
		Text customText = Text.of("Hello, Minecraft!");

		// Render the text on the screen
		textRenderer.draw(matrixStack, customText, x, y, 0xFFFFFF); // 0xFFFFFF is white color
	}
}
