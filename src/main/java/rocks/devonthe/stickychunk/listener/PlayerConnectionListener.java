/*
 * This file is part of StickyChunk by DevOnTheRocks, licensed under GPL-3.0
 *
 * Copyright (C) 2017 DevOnTheRocks
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * The above notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package rocks.devonthe.stickychunk.listener;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.scheduler.Task;
import rocks.devonthe.stickychunk.StickyChunk;
import rocks.devonthe.stickychunk.chunkload.chunkloader.ChunkLoader;
import rocks.devonthe.stickychunk.data.DataStore;
import rocks.devonthe.stickychunk.data.UserData;
import rocks.devonthe.stickychunk.database.SQLiteEntityManager;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PlayerConnectionListener {
	private static final StickyChunk INSTANCE = StickyChunk.getInstance();
	private SQLiteEntityManager entityManager = INSTANCE.getEntityManager();
	private DataStore dataStore = INSTANCE.getDataStore();
	private Logger logger = INSTANCE.getLogger();

	@Listener
	public void onPlayerJoin(ClientConnectionEvent.Join event, @Root Player player) {
		Date now = new Date(java.util.Date.from(Instant.now()).getTime());

		Sponge.getScheduler().createTaskBuilder()
			.execute(src -> {
				// Load user from the database or create a new one if not present
				UserData userData = dataStore.getOrCreateUserData(player.getUniqueId());

				// Force load the player's chunks and update the DataStore
				dataStore.getOrCreateUserData(player).getChunkLoaders().forEach(ChunkLoader::forceChunks);

				// Update the user in the DataStore and Database
				dataStore.getOrCreateUserData(player).setLastSeen(now).update();
				entityManager.getUserEntityManager().save(userData);
			})
			.async()
			.submit(INSTANCE);
	}

	@Listener
	public void onPlayerLeave(ClientConnectionEvent.Disconnect event, @Root Player player) {
		UserData userData = dataStore.getOrCreateUserData(player.getUniqueId());
		Date now = new Date(java.util.Date.from(Instant.now()).getTime());
		ArrayList<ChunkLoader> deleteQueue = Lists.newArrayList();

		// Find all ChunkLoaders that need unloading and update the DataStore
		userData.getChunkLoaders().forEach(chunkLoader ->
			chunkLoader.getOfflineDuration().ifPresent(duration -> {
				if (duration.isZero()) {
					chunkLoader.unForceChunks();
					deleteQueue.add(chunkLoader);
				} else {
					Sponge.getScheduler().createTaskBuilder()
						.delay(duration.toMinutes(), TimeUnit.MINUTES)
						.execute(unloadChunkLoader(chunkLoader, player.getUniqueId()))
						.async()
						.submit(INSTANCE);
				}
			})
		);

		Sponge.getScheduler().createTaskBuilder()
			.execute(src -> {
				// Update the user in the Database
				dataStore.getOrCreateUserData(player).setLastSeen(now).update();
				entityManager.getUserEntityManager().save(userData);

				// Remove the no-longer necessary data from the DataStore
				deleteQueue.forEach(cl -> dataStore.removeChunkLoader(player.getUniqueId(), cl));
			})
			.async()
			.submit(INSTANCE);
	}

	private Consumer<Task> unloadChunkLoader(ChunkLoader chunkLoader, UUID playerId) {
		return src -> {
			chunkLoader.unForceChunks();
			dataStore.removeChunkLoader(playerId, chunkLoader);
		};
	}
}
