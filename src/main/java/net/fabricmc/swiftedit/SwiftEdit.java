package net.fabricmc.swiftedit;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.client.ClientGameSession;
import net.minecraft.client.gui.ClientChatListener;
import net.minecraft.client.render.*;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.world.WorldEvents;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.awt.event.MouseEvent;
import java.util.Objects;

public class SwiftEdit implements ClientModInitializer {

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
	GLFWScrollCallback defaultScroll;
	boolean scrollCallbackSet = false;


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
		WorldRenderEvents.LAST.register(this::OnDrawGuide);
		ServerWorldEvents.UNLOAD.register((world,a)->{
			Deactivate();
		});
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
			if(editMode == 1){
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
						SetScroll();
						regionSetMode = 3;
						SetStackDirection();
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

	public void onMouseScroll(double vertical) {
		if(client.isPaused()) return;
		stackCount = Math.max(stackCount+(int)vertical,0);
		if(stackCount >0) editMode = 1;
		else editMode = 0;
	}

	private void OnDrawGuide(WorldRenderContext context){
		if(!isActivated) return;
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder builder = tessellator.getBuffer();
		builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		MatrixStack matrixStack = context.matrixStack();
		matrixStack.push();

		switch (regionSetMode){
			case 0:
				BlockPos aimpos = GetPos1();
				if(aimpos!=null){
					DrawCube(aimpos,aimpos,0xFFFFFFFF,context,builder);
				}
				break;
			case 1:
				BlockPos planarPos = GetPlanarSize(pos1.getY());
				DrawCube(pos1,planarPos,0xAAFFFFFF,context,builder);
				client.player.sendMessage(Text.of("X:" + (Math.abs(planarPos.getX()-pos1.getX())+1)+" Z:" +(Math.abs(planarPos.getZ()-pos1.getZ())+1)),true);
				break;
			case 2:
				BlockPos heightPos = GetPos2(pos2);
				DrawCube(pos1,heightPos,0xAAFFFFFF,context,builder);
				client.player.sendMessage(Text.of("Height:" + (Math.abs(heightPos.getY()-pos1.getY())+1)),true);
				break;
			case 3:
				if(pos1!=null && pos2!=null){
					DrawCube(pos1,pos2,0xFF01F099,context,builder);
				}
				if(editMode == 0){
					client.player.sendMessage(Text.of("Left Click : §lRemove §rRight Click : §lSet Block §rScroll : §lStack"),true);
				}else if(editMode == 1){
					client.player.sendMessage(Text.of("Left Click : §lCancel §rRight Click : §lStack"),true);
					if(pos1!=null && pos2!=null){
						BlockPos stackOffset = GetMaxBlockPos(pos1, pos2).subtract(GetMinBlockPos(pos1, pos2)).add(1,1,1);
						stackOffset = new BlockPos(stackOffset.getX() * stackVector.getX(), stackOffset.getY() * stackVector.getY(), stackOffset.getZ() * stackVector.getZ());
						for (int i = 1; i<=stackCount; i++) {
							DrawCube(pos1.add(stackOffset.multiply(i)), pos2.add(stackOffset.multiply(i)), 0xFFCCCCCC, context, builder);
						}
					}
				}
				break;
		}

		tessellator.draw();

		// Restore previous rendering states
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		matrixStack.pop();
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
		ResetScroll();
	}

	public void Deactivate(){
		ClearRegion();
		isActivated = false;
		if(client.player != null) client.player.sendMessage(Text.of("Swift Edit §cDeactivated"),true);
	}

	private void SetScroll(){
		if(client.getWindow()!=null && !scrollCallbackSet){
			defaultScroll = GLFW.glfwSetScrollCallback(client.getWindow().getHandle(),(window,xOffset,yOffset) -> {
				onMouseScroll(yOffset);
			});
			scrollCallbackSet = true;
		}
	}
	private void ResetScroll(){
		if(client.getWindow()!=null && scrollCallbackSet){
			GLFW.glfwSetScrollCallback(client.getWindow().getHandle(),defaultScroll);
			scrollCallbackSet = false;
		}
	}

	private void SetStackDirection(){
		double playerYaw = client.player.getYaw();
		double playerPitch = client.player.getPitch();
		if(playerYaw <0) playerYaw = -(Math.abs(playerYaw)%360)+720;
		playerYaw = playerYaw%360;

		if(playerPitch > 45){
			stackDirection = "d";
			stackVector = new BlockPos(0,-1,0);
		}else if(playerPitch < -45){
			stackDirection = "u";
			stackVector = new BlockPos(0,1,0);
		}else if (playerYaw >= 45 && playerYaw < 135) {
			stackDirection = "w";
			stackVector = new BlockPos(-1,0,0);
		} else if (playerYaw >= 135 && playerYaw < 225) {
			stackDirection = "n";
			stackVector = new BlockPos(0,0,-1);
		} else if (playerYaw >= 225 && playerYaw < 315) {
			stackDirection = "e";
			stackVector = new BlockPos(1,0,0);
		} else {
			stackDirection = "s";
			stackVector = new BlockPos(0,0,1);
		}
	}

	private BlockPos GetMinBlockPos(BlockPos a,BlockPos b){
		return new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
	}
	private BlockPos GetMaxBlockPos(BlockPos a, BlockPos b){
		return new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
	}

	private void DrawCube(BlockPos a,BlockPos b,int color, WorldRenderContext context, BufferBuilder bBuilder){
		BlockPos temp = GetMinBlockPos(a,b);
		b = GetMaxBlockPos(a,b);
		a = temp;
		DrawSquare(a,b,color,context,bBuilder);
		b = b.add(0,1,0);
		DrawSquare(a.withY(b.getY()),b,color,context,bBuilder);
		DrawLine(new Vec3d(a.getX(),a.getY(),a.getZ()),new Vec3d(a.getX(),b.getY(),a.getZ()),color,context,bBuilder);
		DrawLine(new Vec3d(b.getX()+1,a.getY(),b.getZ()+1),new Vec3d(b.getX()+1,b.getY(),b.getZ()+1),color,context,bBuilder);
		DrawLine(new Vec3d(a.getX(),a.getY(),b.getZ()+1),new Vec3d(a.getX(),b.getY(),b.getZ()+1),color,context,bBuilder);
		DrawLine(new Vec3d(b.getX()+1,a.getY(),a.getZ()),new Vec3d(b.getX()+1,b.getY(),a.getZ()),color,context,bBuilder);
	}
	private void DrawSquare(BlockPos a,BlockPos b, int color, WorldRenderContext context, BufferBuilder bBuilder){
		BlockPos temp = GetMinBlockPos(a,b);
		b = GetMaxBlockPos(a,b).add(1,0,1);
		a = temp;
		DrawLine(new Vec3d(a.getX(),a.getY(),a.getZ()),new Vec3d(b.getX(),a.getY(),a.getZ()),color,context,bBuilder);
		DrawLine(new Vec3d(a.getX(),a.getY(),b.getZ()),new Vec3d(b.getX(),a.getY(),b.getZ()),color,context,bBuilder);
		DrawLine(new Vec3d(a.getX(),a.getY(),a.getZ()),new Vec3d(a.getX(),a.getY(),b.getZ()),color,context,bBuilder);
		DrawLine(new Vec3d(b.getX(),a.getY(),a.getZ()),new Vec3d(b.getX(),a.getY(),b.getZ()),color,context,bBuilder);
	}
	private void DrawLine(Vec3d a, Vec3d b, int color, WorldRenderContext context, BufferBuilder bBuilder){
		Vec3d pos = context.camera().getPos();
		bBuilder.vertex(a.x - pos.x,a.y - pos.y,a.z - pos.z).color(color).next();
		bBuilder.vertex(b.x - pos.x,b.y - pos.y,b.z - pos.z).color(color).next();
	}
}
