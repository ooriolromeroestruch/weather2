package weather2.weathersystem.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Biomes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import weather2.CommonProxy;
import weather2.client.entity.particle.ParticleSandstorm;
import weather2.util.WeatherUtil;
import weather2.util.WeatherUtilBlock;
import weather2.weathersystem.WeatherManagerBase;
import weather2.weathersystem.wind.WindManager;
import CoroUtil.util.Vec3;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.behavior.ParticleBehaviorSandstorm;
import extendedrenderer.particle.entity.EntityRotFX;

/**
 * spawns in sandy biomes
 * needs high wind event
 * starts small size grows up to something like 80 height
 * needs to sorda stay near sand, to be fed
 * where should position be? stay in sand biome? travel outside it?
 * - main position is moving, where the front of the storm is
 * - store original spawn position, spawn particles of increasing height from spawn to current pos
 * 
 * build up sand like snow?
 * usual crazy storm sounds
 * hurt plantlife leafyness
 * 
 * take sand and relocate it forward in direction storm is pushing, near center of where stormfront is
 * 
 * 
 * @author Corosus
 *
 */
public class WeatherObjectSandstorm extends WeatherObject {

	public int height = 0;
	
	public Vec3 posSpawn = new Vec3(0, 0, 0);
	
	@SideOnly(Side.CLIENT)
	public List<EntityRotFX> listParticlesCloud;
	
	public ParticleBehaviorSandstorm particleBehavior;
	
	public int age = 0;
	//public int maxAge = 20*20;
	
	public int ageFadeout = 0;
	public int ageFadeoutMax = 20*60*1;
	
	//public boolean dying = false;
	public boolean isFrontGrowing = true;
	
	public Random rand = new Random();
	
	public WeatherObjectSandstorm(WeatherManagerBase parManager) {
		super(parManager);
		
		this.stormType = EnumStormType.SAND;
		
		if (parManager.getWorld().isRemote) {
			listParticlesCloud = new ArrayList<EntityRotFX>();
			
		}
	}
	
	public void initSandstormSpawn(Vec3 pos) {
		this.pos = new Vec3(pos);
		
		size = 1;
		maxSize = 100;
		
		//temp start
		/*float angle = manager.getWindManager().getWindAngleForClouds();
		
		double vecX = -Math.sin(Math.toRadians(angle));
		double vecZ = Math.cos(Math.toRadians(angle));
		double speed = 150D;
		
		this.pos.xCoord -= vecX * speed;
		this.pos.zCoord -= vecZ * speed;*/
		//temp end
		
		World world = manager.getWorld();
		int yy = world.getHeight(new BlockPos(pos.xCoord, 0, pos.zCoord)).getY();
		pos.yCoord = yy;
		
		posGround = new Vec3(pos);
		
		this.posSpawn = new Vec3(this.pos);
		
		/*height = 0;
		size = 0;
		
		maxSize = 300;
		maxHeight = 100;*/
	}
	
	public float getSandstormScale() {
		if (isFrontGrowing) {
			return (float)size / (float)maxSize;
		} else {
			return 1F - ((float)ageFadeout / (float)ageFadeoutMax);
		}
	}
	
	public static boolean isDesert(Biome biome) {
		return biome == Biomes.DESERT || biome == Biomes.DESERT_HILLS;
	}
	
	/**
	 * 
	 * - size of storm determined by how long it was in desert
	 * - front of storm dies down once it exits desert
	 * - stops moving once fully dies down
	 * 
	 * - storm continues for minutes even after front has exited desert
	 * 
	 * 
	 * 
	 */
	public void tickProgressionAndMovement() {
		
		World world = manager.getWorld();
		WindManager windMan = manager.getWindManager();
		
		float angle = windMan.getWindAngleForClouds();
		float speedWind = windMan.getWindSpeedForClouds();
		
		/**
		 * Progression
		 */
		
		if (!world.isRemote) {
			age++;
			
			//boolean isGrowing = true;
			
			BlockPos posBlock = pos.toBlockPos();
			
			//only grow if in loaded area and in desert, also prevent it from growing again for some reason if it started dying already
			if (isFrontGrowing && world.isBlockLoaded(posBlock)) {
				Biome biomeIn = world.getBiomeForCoordsBody(posBlock);
				
				//TODO: more generic desert detection
				if (isDesert(biomeIn)) {
					isFrontGrowing = true;
				} else {
					System.out.println("sandstorm fadeout started");
					isFrontGrowing = false;
				}
			} else {
				isFrontGrowing = false;
			}
			
			int sizeAdjRate = 10;
			
			if (isFrontGrowing) {
				if (world.getTotalWorldTime() % sizeAdjRate == 0) {
					if (size < maxSize) {
						size++;
						System.out.println("size: " + size);
					}
				}
			} else {
				if (world.getTotalWorldTime() % sizeAdjRate == 0) {
					if (size > 0) {
						size--;
						System.out.println("size: " + size);
					}
				}
				
				//fadeout till death
				if (ageFadeout < ageFadeoutMax) {
					ageFadeout++;
				} else {
					System.out.println("sandstorm died");
					this.setDead();
				}
			}
			
			//System.out.println("sandstorm age: " + age);
			//will die once it builds down
			/*if (age >= maxAge) {
				this.setDead();
				return;
			}*/
			
			
			
		}
		
		/**
		 * Movement
		 */
		
		//clouds move at 0.2 amp of actual wind speed
		
		double vecX = -Math.sin(Math.toRadians(angle));
		double vecZ = Math.cos(Math.toRadians(angle));
		double speed = speedWind * 0.3D;//0.2D;
		
		//prevent it from moving if its died down to nothing
		if (size > 0) {
			this.pos.xCoord += vecX * speed;
			this.pos.zCoord += vecZ * speed;
		}
		
		//wind movement
		//this.motion = windMan.applyWindForceImpl(this.motion, 5F, 1F/20F, 0.5F);
		
		/*this.pos.xCoord += this.motion.xCoord;
		this.pos.yCoord += this.motion.yCoord;
		this.pos.zCoord += this.motion.zCoord;*/
		
		int yy = world.getHeight(new BlockPos(pos.xCoord, 0, pos.zCoord)).getY();
		
		this.pos.yCoord = yy + 1;
	}
	
	public void tickBlockSandBuildup() {

		World world = manager.getWorld();
		WindManager windMan = manager.getWindManager();
		
		float angle = windMan.getWindAngleForClouds();
		
		//keep it set to do a lot of work only occasionally, prevents chunk render update spam for client which kills fps 
		int delay = 40;
		int loop = 800;
		
		//sand block buildup
		if (!world.isRemote) {
			if (world.getTotalWorldTime() % delay == 0) {
				
		    	for (int i = 0; i < loop; i++) {
		    		
		    		double xVec = this.pos.xCoord - rand.nextInt(size / 2) + rand.nextInt(size);
			    	double zVec = this.pos.zCoord - rand.nextInt(size / 2) + rand.nextInt(size);
			    	
			    	int x = MathHelper.floor_double(xVec);
			    	int z = MathHelper.floor_double(zVec);
			    	int y = world.getHeight(new BlockPos(x, 0, z)).getY();
			    	
			    	float angleRand = (rand.nextFloat() - rand.nextFloat()) * 360F;
			    	
			    	Vec3 vec = new Vec3(x, y, z);
			    	
			    	//avoid unloaded areas
			    	if (!world.isBlockLoaded(vec.toBlockPos())) continue;
			    	
			    	WeatherUtilBlock.fillAgainstWallSmoothly(world, vec, angle/* + angleRand*/, 15, 2, CommonProxy.blockSandLayer);
			    	
		    	}
			}
		}
	}
	
	@Override
	public void tick() {
		super.tick();
		
		if (manager == null) {
			System.out.println("WeatherManager is null for " + this + ", why!!!");
			return;
		}
		
		World world = manager.getWorld();
		WindManager windMan = manager.getWindManager();
		
		if (world == null) {
			System.out.println("world is null for " + this + ", why!!!");
			return;
		}
		
		if (WeatherUtil.isPausedSideSafe(world)) return;
		
		
		
		tickProgressionAndMovement();
		
		int yy = world.getHeight(new BlockPos(pos.xCoord, 0, pos.zCoord)).getY();
		
		
		
		
		
		if (world.isRemote) {
			tickClient();
		}
		
		if (size >= 2) {
			tickBlockSandBuildup();
		}
		
		this.posGround.xCoord = pos.xCoord;
		this.posGround.yCoord = yy;
		this.posGround.zCoord = pos.zCoord;
		
	}
	
	@SideOnly(Side.CLIENT)
	public void tickClient() {
		
		//moved
		//if (WeatherUtil.isPaused()) return;
		
		Minecraft mc = Minecraft.getMinecraft();
		World world = manager.getWorld();
		WindManager windMan = manager.getWindManager();
		
		if (particleBehavior == null) {
			particleBehavior = new ParticleBehaviorSandstorm(pos);
		}
		
		//double size = 15;
    	//double height = 50;
    	double distanceToCenter = pos.distanceTo(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
    	//how close to renderable particle wall
    	double distanceToFront = distanceToCenter - size;
    	boolean isInside = distanceToFront < 0;
    	
    	double circ = Math.PI * size;
    	
    	//double scale = 10;
    	double distBetweenParticles = 3;
    	
    	//double circScale = circ / distBetweenParticles;
    	
    	/**
    	 * if circ is 10, 10 / 3 size = 3 particles
    	 * if 30 circ / 3 size = 10 particles
    	 * if 200 circ / 3 size = 66 particles
    	 * 
    	 * how many degrees do we need to jump, 
    	 * 360 / 3 part = 120
    	 * 360 / 10 part = 36
    	 * 360 / 66 part = 5.4
    	 * 
    	 */
    	
    	//need steady dist between particles
    	
    	double degRate = 360D / (circ / distBetweenParticles);
    	
    	if (mc.theWorld.getTotalWorldTime() % 40 == 0) {
    		//System.out.println("circ: " + circ);
    		//System.out.println("degRate: " + degRate);
    	}
    	
    	Random rand = mc.theWorld.rand;
    	
    	this.height = this.size / 4;
    	int heightLayers = Math.max(1, this.height / (int) distBetweenParticles);
    	
    	if ((mc.theWorld.getTotalWorldTime()) % 10 == 0) {
    		//System.out.println(heightLayers);
    	}
    	
    	double distFromSpawn = this.posSpawn.distanceTo(this.pos);
    	
    	double xVec = this.posSpawn.xCoord - this.pos.xCoord;
    	double zVec = this.posSpawn.zCoord - this.pos.zCoord;
    	
    	double directionAngle = Math.atan2(zVec, xVec);
    	
    	/**
    	 * 
    	 * ideas: 
    	 * - pull particle distance inwards as its y reduces
    	 * -- factor in initial height spawn, first push out, then in, for a circularly shaped effect vertically
    	 * - base needs to be bigger than upper area
    	 * -- account for size change in the degRate value calculations for less particle spam
    	 * - needs more independant particle motion, its too unified atm
    	 * - irl sandstorms last between hours and days, adjust time for mc using speed and scale and lifetime
    	 */
    	
    	double directionAngleDeg = Math.toDegrees(directionAngle);
    	
    	int spawnedThisTick = 0;
    	
    	//stormfront wall
    	for (int heightLayer = 0; heightLayer < heightLayers; heightLayer++) {
    		//youd think this should be angle - 90 to angle + 90, but minecraft / bad math
		    for (double i = directionAngleDeg; i < directionAngleDeg + (180); i += degRate) {
		    	if ((mc.theWorld.getTotalWorldTime()) % 40 == 0) {
		    		
		    		double sizeSub = heightLayer * 2D;
		    		double sizeDyn = size - sizeSub;
		    		double inwardsAdj = rand.nextDouble() * 5D;//(sizeDyn * 0.75D);
		    		
		    		double sizeRand = (sizeDyn + /*rand.nextDouble() * 30D*/ - inwardsAdj/*30D*/)/* / (double)heightLayer*/;
		    		double x = pos.xCoord + (-Math.sin(Math.toRadians(i)) * (sizeRand));
		    		double z = pos.zCoord + (Math.cos(Math.toRadians(i)) * (sizeRand));
		    		double y = pos.yCoord + (heightLayer * distBetweenParticles * 2);
		    		
		    		TextureAtlasSprite sprite = ParticleRegistry.cloud256;
		    		
		    		ParticleSandstorm part = new ParticleSandstorm(mc.theWorld, x, y, z
		    				, 0, 0, 0, sprite);
		    		particleBehavior.initParticle(part);
		    		
		    		part.angleToStorm = i;
		    		part.distAdj = sizeRand;
		    		part.heightLayer = heightLayer;
		    		part.lockPosition = true;
		    		
		    		part.setFacePlayer(false);
		    		part.isTransparent = true;
		    		part.rotationYaw = (float) i + rand.nextInt(20) - 10;//Math.toDegrees(Math.cos(Math.toRadians(i)) * 2D);
		    		part.rotationPitch = 0;
		    		part.setMaxAge(300);
		    		part.setGravity(0.09F);
		    		part.setAlphaF(1F);
		    		float brightnessMulti = 1F - (rand.nextFloat() * 0.5F);
		    		part.setRBGColorF(0.65F * brightnessMulti, 0.6F * brightnessMulti, 0.3F * brightnessMulti);
		    		part.setScale(100);
		    		
		    		//part.windWeight = 5F;
		    		
		    		part.setKillOnCollide(true);
		    		
		    		particleBehavior.particles.add(part);
		    		part.spawnAsWeatherEffect();
		    		
		    		spawnedThisTick++;
		    		
		    		//only need for non managed particles
		    		//ClientTickHandler.weatherManager.addWeatheredParticle(part);
		    		
		    		//mc.effectRenderer.addEffect(part);
		    		
		    		
		    	}
	    	}
    	}
    	
    	
    	if (spawnedThisTick > 0) {
    		//System.out.println("spawnedThisTickv1: " + spawnedThisTick);
    		spawnedThisTick = 0;
    	}
    	
    	//half of the angle (?)
    	double spawnAngle = Math.atan2((double)this.maxSize/*this.size*//* / 2D*/, distFromSpawn);
    	
    	//temp test fix
    	spawnAngle = Math.atan2((double)this.size/* / 2D*/, distFromSpawn);
    	
    	//tweaking for visual due to it moving, etc
    	spawnAngle *= 1.2D;
    	
    	double spawnDistInc = 10;
    	
    	double extraDistSpawnIntoWall = size / 2D;
    	
    	/**
    	 * Spawn particles between spawn pos and current pos, cone shaped
    	 */
    	if ((mc.theWorld.getTotalWorldTime()) % 10 == 0) {
    		
    		//System.out.println(this.particleBehavior.particles.size());
    		
	    	for (double spawnDistTick = 0; spawnDistTick < distFromSpawn + (extraDistSpawnIntoWall); spawnDistTick += spawnDistInc) {
	    		//add 1/4 PI for some reason, converting math to mc I guess
	    		double randAngle = directionAngle + (Math.PI / 2D) - (spawnAngle) + (rand.nextDouble() * spawnAngle * 2D);

	    		double randHeight = (spawnDistTick / distFromSpawn) * height * 1.2D * rand.nextDouble();
	    		
	    		//project out from spawn point, towards a point within acceptable angle
	    		double x = posSpawn.xCoord + (-Math.sin(/*Math.toRadians(*/randAngle/*)*/) * (spawnDistTick));
	    		double z = posSpawn.zCoord + (Math.cos(/*Math.toRadians(*/randAngle/*)*/) * (spawnDistTick));
	    		//TODO: account for terrain adjustments
	    		int yy = world.getHeight(new BlockPos(pos.xCoord, 0, pos.zCoord)).getY();
	    		double y = yy/*posSpawn.yCoord*/ + 2 + randHeight;
	    		
	    		TextureAtlasSprite sprite = ParticleRegistry.cloud256;
	    		
	    		ParticleSandstorm part = new ParticleSandstorm(mc.theWorld, x, y, z
	    				, 0, 0, 0, sprite);
	    		particleBehavior.initParticle(part);
	    		
	    		part.setFacePlayer(false);
	    		part.isTransparent = true;
	    		part.rotationYaw = (float)rand.nextInt(360);
	    		part.rotationPitch = (float)rand.nextInt(360);
	    		part.setMaxAge(100);
	    		part.setGravity(0.09F);
	    		part.setAlphaF(1F);
	    		float brightnessMulti = 1F - (rand.nextFloat() * 0.5F);
	    		part.setRBGColorF(0.65F * brightnessMulti, 0.6F * brightnessMulti, 0.3F * brightnessMulti);
	    		part.setScale(100);
	    		
	    		part.setKillOnCollide(true);
	    		
	    		part.windWeight = 1F;
	    		
	    		particleBehavior.particles.add(part);
	    		//ClientTickHandler.weatherManager.addWeatheredParticle(part);
	    		part.spawnAsWeatherEffect();
	    		
	    		spawnedThisTick++;
	    	}
    	}
    	
    	if (spawnedThisTick > 0) {
    		//System.out.println("spawnedThisTickv2: " + spawnedThisTick);
    	}

	    float angle = windMan.getWindAngleForClouds();
		
		double vecX = -Math.sin(Math.toRadians(angle));
		double vecZ = Math.cos(Math.toRadians(angle));
		double speed = 0.8D;
		
		
	    
		particleBehavior.coordSource = pos;
	    particleBehavior.tickUpdateList();
	    
	    //System.out.println("client side size: " + size);
	    
	    //weather specific updates
	    for (int i = 0; i < particleBehavior.particles.size(); i++) {
	    	ParticleSandstorm particle = (ParticleSandstorm) particleBehavior.particles.get(i);
	    	
	    	
	    	
	    	if (particle.lockPosition) {
		    	double x = pos.xCoord + (-Math.sin(Math.toRadians(particle.angleToStorm)) * (particle.distAdj));
	    		double z = pos.zCoord + (Math.cos(Math.toRadians(particle.angleToStorm)) * (particle.distAdj));
	    		double y = pos.yCoord + (particle.heightLayer * distBetweenParticles);
	    		
	    		moveToPosition(particle, x, y, z, 0.01D);
	    	} else {
	    		particle.setMotionX(/*particle.getMotionX() + */(vecX * speed));
		    	particle.setMotionZ(/*particle.getMotionZ() + */(vecZ * speed));
	    	}
    		//windMan.applyWindForceNew(particle);
    		
	    }
	    
	    //System.out.println("spawn particles at: " + pos);
	}
	
	public void moveToPosition(ParticleSandstorm particle, double x, double y, double z, double maxSpeed) {
		if (particle.getPosX() > x) {
			particle.setMotionX(particle.getMotionX() + -maxSpeed);
		} else {
			particle.setMotionX(particle.getMotionX() + maxSpeed);
		}
		
		/*if (particle.getPosY() > y) {
			particle.setMotionY(particle.getMotionY() + -maxSpeed);
		} else {
			particle.setMotionY(particle.getMotionY() + maxSpeed);
		}*/
		
		if (particle.getPosZ() > z) {
			particle.setMotionZ(particle.getMotionZ() + -maxSpeed);
		} else {
			particle.setMotionZ(particle.getMotionZ() + maxSpeed);
		}
		
		
		double distXZ = Math.sqrt((particle.getPosX() - x) * 2 + (particle.getPosZ() - z) * 2);
		if (distXZ < 5D) {
			particle.setMotionX(particle.getMotionX() * 0.8D);
			particle.setMotionZ(particle.getMotionZ() * 0.8D);
		}
	}
	
	@Override
	public NBTTagCompound nbtSyncForClient(NBTTagCompound nbt) {
		NBTTagCompound data = super.nbtSyncForClient(nbt);
		
		data.setDouble("posSpawnX", posSpawn.xCoord);
		data.setDouble("posSpawnY", posSpawn.yCoord);
		data.setDouble("posSpawnZ", posSpawn.zCoord);
		
		data.setInteger("ageFadeout", this.ageFadeout);
		data.setInteger("ageFadeoutMax", this.ageFadeoutMax);
		
		/*data.setLong("ID", ID);
		data.setInteger("size", size);
		data.setInteger("maxSize", maxSize);*/
		return data;
	}
	
	@Override
	public void nbtSyncFromServer(NBTTagCompound parNBT) {
		super.nbtSyncFromServer(parNBT);
		
		posSpawn = new Vec3(parNBT.getDouble("posSpawnX"), parNBT.getDouble("posSpawnY"), parNBT.getDouble("posSpawnZ"));
		
		this.ageFadeout = parNBT.getInteger("ageFadeout");
		this.ageFadeoutMax = parNBT.getInteger("ageFadeoutMax");
	}

}