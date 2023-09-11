package net.fabricmc.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.Vec3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
	public static boolean isActivated = false;
	public static boolean isSurfaceMode = true; // Surface Mode = starts region from the adjacent block
	public static int regionSetMode = 0; // 0=none, 1=planar, 2=height, 3=done
	public static BlockPos pos1;
	public static BlockPos pos2;

	// KEY BINDS
	public static KeyBinding kToggleQE;
	public static KeyBinding kLeftClick = client.options.attackKey;
	public static KeyBinding kRightClick = client.options.useKey;


	@Override
	public void onInitializeClient() {
		// REGISTER KEYBIND
		kToggleQE = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.swift_edit.toggle", GLFW.GLFW_KEY_LEFT_ALT, "key.categories.swift_edit")
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// END CLIENT TICK

			if(isActivated){
				if(kLeftClick.isPressed()){
					switch (regionSetMode){
						case 0:
							pos1 = GetAimingPosition();
							if(pos1 != null) regionSetMode = 1;
							break;
						case 1:
							pos2 = GetPlanarPosition(pos1.getY());
							regionSetMode =2;
							break;
						case 2:
							pos2 = GetHeight(pos2);
							break;
						case 3:
							Command("//set 0");
							Command("//desel");
							pos1 = null;
							pos2 = null;
							break;
					}
				}
			}

		});

		WorldRenderEvents.BEFORE_DEBUG_RENDER.register((context) -> {
			// RENDER DEBUG
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
}
