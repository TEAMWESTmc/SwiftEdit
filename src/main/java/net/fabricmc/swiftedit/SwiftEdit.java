package net.fabricmc.swiftedit;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.world.RaycastContext;
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

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;

public class SwiftEdit implements ClientModInitializer, ScreenMouseEvents.AfterMouseScroll {

	public static final MinecraftClient client = MinecraftClient.getInstance();
	public static final DebugRenderer debugRenderer = client.debugRenderer;

	public static boolean isActivated = false;
	public static boolean isSurfaceMode = false; // Surface Mode = starts region from the adjacent block
	public static int regionSetMode = 0; // 0=none, 1=planar, 2=height, 3=done
	public static int editMode = 0; // 0=set, 1=stack, 2=copy
	public static BlockPos pos1;
	public static BlockPos pos2;
	public static int stackCount = 0;
	public static BlockPos stackVector;
	public static String stackDirection;

	// KEY BINDS
	public static KeyBinding kToggleQE;
	public static boolean onKToggleQE = true;
	public static KeyBinding kToggleSurface;
	public static boolean onKToggleSurface = true;
	public static KeyBinding kLeftClick;
	public static boolean onKLeftClick = true;
	public static KeyBinding kRightClick;
	public static boolean onKRightClick = true;


	@Override
	public void onInitializeClient() {
		Deactivate();

		// REGISTER KEYBIND
		kToggleQE = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.swift_edit.toggle", GLFW.GLFW_KEY_LEFT_ALT, "key.categories.swift_edit")
		);
		kToggleSurface = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.swift_edit.surface_mode", GLFW.GLFW_KEY_CAPS_LOCK, "key.categories.swift_edit")
		);

		ClientTickEvents.END_CLIENT_TICK.register(this::OnClientTick);
		WorldRenderEvents.LAST.register(this::OnDrawDebug);
		//WorldRenderEvents.LAST.register(this::OnDrawGuide);
	}

	private void OnClientTick(MinecraftClient client){
		if(client == null) return;
		if(kLeftClick==null) kLeftClick = client.options.attackKey;
		if(kRightClick==null) kRightClick = client.options.useKey;


		// TOGGLE
		if(onKToggleQE && kToggleQE.isPressed()){
			if(isActivated) Deactivate();
			else Activate();
			onKToggleQE = false;
		}else if(!kToggleQE.isPressed()) onKToggleQE = true;

		if(isActivated){
			if(stackCount>0){
				editMode = 1;
				SetStackDirection();
			}
			// LEFT CLICK
			if(onKLeftClick && kLeftClick.isPressed()){
				switch (regionSetMode){
					case 0:
						// click on nothing
						pos1 = GetPos1();
						if(pos1 != null){
							Command("//pos1 "+pos1.getX()+","+pos1.getY()+","+pos1.getZ());
							regionSetMode = 1;
						}
						break;
					case 1:
						// has set first position
						pos2 = GetPlanarSize(pos1.getY());
						regionSetMode =2;
						break;
					case 2:
						// has set planar area
						pos2 = GetPos2(pos2);
						Command("//pos2 "+pos2.getX()+","+pos2.getY()+","+pos2.getZ());
						regionSetMode = 3;
						break;
					case 3:
						// has set region
						if(editMode == 0){
							Command("//set 0");
							client.player.sendMessage(Text.of("Remove"),true);
							ClearRegion();
						}else if(editMode == 1){
							editMode = 0;
							stackCount = 0;
						}
						break;
				}
				onKLeftClick = false;
			} else if (!kLeftClick.isPressed()) onKLeftClick = true;

			// RIGHT CLICK
			if (onKRightClick && kRightClick.isPressed()){
				if(regionSetMode == 1 || regionSetMode == 2){
					ClearRegion();
				} else if (regionSetMode == 3) {
					if(editMode==0){
						client.player.sendMessage(Text.of("Set Block"),true);
						Command("//set hand");
						ClearRegion();
					}else if(editMode ==1){
						client.player.sendMessage(Text.of("Stack Selection : " + stackCount),true);
						Command("//stack " + stackCount + " " + stackDirection);
						ClearRegion();
					}
				}
				onKRightClick = false;
			} else if (!kRightClick.isPressed()) onKRightClick = true;

			// TOGGLE SURFACE MODE
			if (onKToggleSurface && kToggleSurface.isPressed()){
				isSurfaceMode = !isSurfaceMode;
				if(isSurfaceMode) client.player.sendMessage(Text.of("Surface Mode §aOn"),true);
				else client.player.sendMessage(Text.of("Surface Mode §cOff"),true);
				onKToggleSurface = false;
			} else if(!kToggleSurface.isPressed()) onKToggleSurface = true;

		}

	}

	@Override
	public void afterMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		Command("scroll" + verticalAmount);
	}

	private void OnDrawGuide(WorldRenderContext context){
		if(!isActivated) return;
		// Clear any previously set transformations or states
		RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
		RenderSystem.clearDepth(1.0);
		RenderSystem.disableTexture();

		// Draw the quad
		RenderSystem.lineWidth(20);
		RenderSystem.disableCull();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder builder = tessellator.getBuffer();
		builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		// Define the quad's vertices with color (RGBA)
		builder.vertex(0, 0, 0).color(255, 255, 255, 255).next();
		builder.vertex(0, 0, 20).color(255, 255, 255, 255).next();
		builder.vertex(20, 0, -20).color(255, 255, 255, 255).next();
		builder.vertex(20, 0, 0).color(255, 255, 255, 255).next();

		tessellator.draw();

		// Restore previous rendering states
		RenderSystem.enableCull();
		RenderSystem.enableTexture();
	}

	private void OnDrawDebug(WorldRenderContext worldRenderContext){
		// RENDER DEBUG
		if(isActivated){
			switch (regionSetMode){
				case 0:
					BlockPos aimPos = GetPos1();
					if( aimPos !=null ) debugRenderer.drawBox(GetPos1(),0,1.0F,0.0F,0.0F,0.1F);
					break;
				case 1:
					BlockPos planarPos = GetPlanarSize(pos1.getY()).add(0,1,0);
					debugRenderer.drawBox(planarPos.add(0,-1,0),0,1,1,1,0.1f);
					debugRenderer.drawBox(GetMinBlockPos(pos1,planarPos),GetMaxBlockPos(pos1,planarPos).add(1,0,1),1,1,1,0.1F);
					client.player.sendMessage(Text.of("X:" + (Math.abs(planarPos.getX()-pos1.getX())+1)+" Z:" +(Math.abs(planarPos.getZ()-pos1.getZ())+1)),true);
					break;
				case 2:
					BlockPos heightPos = GetPos2(pos2);
					debugRenderer.drawBox(heightPos,0,1,1,1,0.1f);
					debugRenderer.drawBox(GetMinBlockPos(pos1,heightPos),GetMaxBlockPos(pos1,heightPos).add(1,1,1),1,1,1,0.1f);
					client.player.sendMessage(Text.of("Height:" + (Math.abs(heightPos.getY()-pos1.getY())+1)),true);
					break;
				case 3:
					debugRenderer.drawBox(GetMinBlockPos(pos1,pos2),GetMaxBlockPos(pos1,pos2).add(1,1,1),1,1,1,0.1f);
					client.player.sendMessage(Text.of("Left Click : §lRemove \\nRight Click : §lSet Block\\nScroll : §lStack"),true);
					break;
			}
		}
	}

	public BlockPos GetPos1(){
		Entity camera = client.getCameraEntity();
		HitResult hit = client.world.raycast(new RaycastContext(camera.getCameraPosVec(1.0F), camera.getCameraPosVec(1.0F).add(camera.getRotationVec(1.0F).multiply(50)),
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, camera
		));
		if(hit.getType() != HitResult.Type.BLOCK) return null;

		BlockHitResult blockHit = (BlockHitResult) hit;

		BlockPos blockPos = blockHit.getBlockPos();

		if (isSurfaceMode) {
			blockPos = blockPos.offset(blockHit.getSide());
		}
		return blockPos;
	}

	public BlockPos GetPlanarSize(int yPos){
		Vec3d cameraPos = Objects.requireNonNull(client.getCameraEntity()).getPos();
		Vec3d cameraView = client.getCameraEntity().getRotationVec(1.0F);

		// Calculate the intersection point with the specified Y-plane.
		double t = (yPos - cameraPos.y) / cameraView.y;
		double blockX = cameraPos.x + t * cameraView.x;
		double blockZ = cameraPos.z + t * cameraView.z;

		return new BlockPos(blockX, yPos, blockZ);
	}

	public BlockPos GetPos2(BlockPos pPos){
		Vec3d viewRot = client.getCameraEntity().getRotationVec(1.0F);
		Vec3d pPosd = new Vec3d(pPos.getX(), pPos.getY(), pPos.getZ());
		double distance = client.player.getPos().distanceTo(new Vec3d(pPosd.x,client.player.getPos().y, pPosd.getZ()));
		Vec3d target = viewRot.multiply(distance/viewRot.distanceTo(new Vec3d(0,viewRot.y,0)));
		return pPos.withY((int)(target.getY()+client.player.getPos().y));
	}

	public static void Command(String command) {
		if (client != null && client.player != null) {
			client.player.sendChatMessage(command);
		}
	}

	public void Activate(){
		isActivated = true;
		client.player.sendMessage(Text.of("Swift Edit §aActivated"),true);
	}

	public void ClearRegion(){
		Command("//desel");
		pos1 = null;
		pos2 = null;
		regionSetMode = 0;
		editMode = 0;
		stackCount = 0;
		onKLeftClick = true;
		onKRightClick = true;
	}

	public void Deactivate(){
		ClearRegion();
		isActivated = false;
		client.player.sendMessage(Text.of("Swift Edit §cDeactivated"),true);
	}

	private void SetStackDirection(){
		double playerYaw = client.player.getYaw();
		double playerPitch = client.player.getPitch();

		if(playerPitch > 45){
			stackDirection = "u";
			stackVector = new BlockPos(0,1,0);
		}else if(playerPitch < -45){
			stackDirection = "d";
			stackVector = new BlockPos(0,-1,0);
		}else if (playerYaw >= 45 && playerYaw < 135) {
			stackDirection = "w";
			stackVector = new BlockPos(1,0,0);
		} else if (playerYaw >= 135 && playerYaw < 225) {
			stackDirection = "n";
			stackVector = new BlockPos(0,0,1);
		} else if (playerYaw >= 225 && playerYaw < 315) {
			stackDirection = "e";
			stackVector = new BlockPos(-1,0,0);
		} else {
			stackDirection = "s";
			stackVector = new BlockPos(0,0,-1);
		}
	}

	private BlockPos GetMinBlockPos(BlockPos a,BlockPos b){
		return new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
	}
	private BlockPos GetMaxBlockPos(BlockPos a, BlockPos b){
		return new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
	}


}
