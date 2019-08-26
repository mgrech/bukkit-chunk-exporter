package dev.mgrech.bukkit.chunkexporter;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin
{
	private interface CoordinateOrder
	{
		int toIndex(int x, int y, int z);
	}

	private static final Map<String, CoordinateOrder> ORDERS = new HashMap<>();

	static
	{
		ORDERS.put("xyz", (x, y, z) -> x * 256 * 16 + y *  16 + z);
		ORDERS.put("xzy", (x, y, z) -> x * 256 * 16 + z * 256 + y);
		ORDERS.put("yxz", (x, y, z) -> y *  16 * 16 + x *  16 + z);
		ORDERS.put("yzx", (x, y, z) -> y *  16 * 16 + z *  16 + x);
		ORDERS.put("zxy", (x, y, z) -> z * 256 * 16 + x * 256 + y);
		ORDERS.put("zyx", (x, y, z) -> z * 256 * 16 + y +  16 + z);
	}

	private void dumpChunk(Path dirPath, Chunk chunk, CoordinateOrder order) throws IOException
	{
		System.out.println(String.format("Exporting chunk (%s, %s) ...", chunk.getX(), chunk.getZ()));

		byte[] result = new byte[2 * 16 * 16 * 256];

		// yzx as used in the anvil format, hopefully this is faster
		for(var y = 0; y != 256; ++y)
			for(var z = 0; z != 16; ++z)
				for(var x = 0; x != 16; ++x)
				{
					var block = chunk.getBlock(x, y, z);
					var id = block.getType().getId();
					var meta = block.getData();

					if(id >= 4096)
						throw new RuntimeException("This should never happen: id >= 4096");

					if(meta >= 16)
						throw new RuntimeException("This should never happen: meta >= 16");

					var index = 2 * order.toIndex(x, y, z);
					result[index]     = (byte)id;
					result[index + 1] = (byte)((id >> 4) | (meta << 4));
				}

		var filePath = String.format("%s_%s.bin", chunk.getX(), chunk.getZ());
		Files.write(dirPath.resolve(filePath), result);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if(!command.getName().equalsIgnoreCase("export"))
			return false;

		if(args.length != 6)
		{
			sender.sendMessage(command.getUsage());
			return true;
		}

		var world = Bukkit.getWorld(args[0]);
		var firstX = Integer.parseInt(args[1]);
		var firstZ = Integer.parseInt(args[2]);
		var lastX = Integer.parseInt(args[3]);
		var lastZ = Integer.parseInt(args[4]);
		var orderName = args[5].toLowerCase();
		var order = ORDERS.get(orderName);

		if(world == null)
		{
			sender.sendMessage("Invalid world name");
			return true;
		}

		if(order == null)
		{
			sender.sendMessage("Invalid coordinate order");
			return true;
		}

		if(firstX > lastX || firstZ > lastZ)
		{
			sender.sendMessage("Invalid chunk range");
			return true;
		}

		try
		{
			var chunkDirPath = Paths.get(String.format("export/%s/%s", world.getName(), orderName));
			Files.createDirectories(chunkDirPath);

			for(var x = firstX; x <= lastX; ++x)
				for(var z = firstZ; z <= lastZ; ++z)
					dumpChunk(chunkDirPath, world.getChunkAt(x, z), order);
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}

		return true;
	}

	@Override
	public void onEnable()
	{
		getCommand("export").setExecutor(this);
	}
}
