package net.fabricmc.swiftedit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.font.TextRenderer;
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
	public static final Logger LOGGER = LoggerFactory.getLogger("swift_edit");
	public static final TextRenderer textRenderer = client.textRenderer;
	public static final DebugRenderer debugRenderer = client.debugRenderer;

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
	public static KeyBinding kLeftClick;
	public static boolean onKLeftClick = true;
	public static KeyBinding kRightClick;
	public static boolean onKRightClick = true;

	public double lineW = 1d;


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

		ClientTickEvents.END_CLIENT_TICK.register(this::OnClientTick);

		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::OnDrawDebug);
		/*
		// Register a callback to render custom HUD elements
		HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> {
			renderCustomText(matrixStack);
		});
		*/
	}

	private void OnClientTick(MinecraftClient client){
		if(client == null) return;
		if(kLeftClick==null) kLeftClick = client.options.attackKey;
		if(kRightClick==null) kRightClick = client.options.useKey;

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
						regionSetMode = 3;
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

	}

	private void OnDrawDebug(WorldRenderContext worldRenderContext){
		// RENDER DEBUG
		if(isActivated){
			switch (regionSetMode){
				case 0:
					BlockPos aimPos = GetAimingPosition();
					if( aimPos !=null ) debugRenderer.drawBox(GetAimingPosition(),0,1.0F,0.0F,0.0F,0.1F);
					break;
				case 1:
					BlockPos planarPos = GetPlanarPosition(pos1.getY()).add(0,1,0);
					debugRenderer.drawBox(planarPos.add(0,-1,0),0,1,1,1,0.1f);
					debugRenderer.drawBox(GetMinBlockPos(pos1,planarPos),GetMaxBlockPos(pos1,planarPos).add(1,0,1),1,1,1,0.1F);
					break;
				case 2:
					BlockPos heightPos = GetHeight(pos2);
					debugRenderer.drawBox(heightPos,0,1,1,1,0.1f);
					debugRenderer.drawBox(GetMinBlockPos(pos1,heightPos),GetMaxBlockPos(pos1,heightPos).add(1,1,1),1,1,1,0.1f);
					break;
				case 3:
					break;
			}
		}
	}

	public BlockPos GetAimingPosition(){
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
		Command("isActivated");
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

	private void DrawSquareFromToXZ(Vec3d min, Vec3d max){
		DrawLine(min.add(0,0,0),max.add(0,0,-(max.z-min.z))); // _
		DrawLine(min.add(0,0,(max.z-min.z)),max.add(0,0,0)); // -
		DrawLine(min.add(0,0,0),max.add(-(max.x-min.x),0,0)); // |.
		DrawLine(min.add((max.x-min.x),0,0),max.add(0,0,0)); // .|
	}
	private void DrawSquareXZ(Vec3d center,double extent){
		DrawLine(center.add(-extent,0,-extent),center.add(extent,0,-extent)); // _
		DrawLine(center.add(-extent,0,-extent),center.add(-extent,0,+extent)); // |.
		DrawLine(center.add(extent,0,-extent),center.add(extent,0,+extent)); // .|
		DrawLine(center.add(-extent,0,extent),center.add(extent,0,extent)); // -
	}
	private void DrawLine(Vec3d min,Vec3d max){
		debugRenderer.drawBox(
				min.x- lineW,min.y- lineW,min.z- lineW,max.x+ lineW,max.y+ lineW,max.z+ lineW,
				1,1,1,1);
	}
	private Vec3d GetMinVec(BlockPos a, BlockPos b){
		return new Vec3d(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
	}
	private Vec3d GetMaxVec(BlockPos a, BlockPos b){
		return new Vec3d(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
	}
	private BlockPos GetMinBlockPos(BlockPos a,BlockPos b){
		return new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
	}
	private BlockPos GetMaxBlockPos(BlockPos a, BlockPos b){
		return new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
	}
	private Vec3d MinX(Vec3d a, Vec3d b){return a.x < b.x ? a:b;}
	private Vec3d MaxX(Vec3d a, Vec3d b){return a.x > b.x ? a:b;}
	private Vec3d MinZ(Vec3d a, Vec3d b){return a.z < b.z ? a:b;}
	private Vec3d MaxZ(Vec3d a, Vec3d b){return a.z > b.z ? a:b;}
}
