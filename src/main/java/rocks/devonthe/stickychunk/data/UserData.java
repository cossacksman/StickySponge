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
package rocks.devonthe.stickychunk.data;

import com.google.common.collect.Lists;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.World;
import rocks.devonthe.stickychunk.StickyChunk;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public class UserData {
	UUID player;
	private Date seen;
	private Date joined;
	private UniqueAccount account;
	private HashMap<String, ChunkLoaderData> loadedChunks;

	public UserData(UUID id, Date joined, Date seen) {
		this.player = id;
		this.seen = seen;
		this.joined = joined;

		StickyChunk.getInstance().getEconomyManager().ifPresent(economyManager -> {
			Optional<UniqueAccount> oAccount = economyManager.getOrCreateAccount(id);
			oAccount.ifPresent(uniqueAccount -> this.account = uniqueAccount);
		});
	}

	public UUID getUniqueId() {
		return player;
	}

	public Date getLastSeen() {
		return seen;
	}

	public UserData setLastSeen(Date seen) {
		this.seen = seen;
		return this;
	}

	public Date getUserJoined() {
		return joined;
	}

	public BigDecimal getBalance(Currency currency) {
		return account.getBalance(currency);
	}

	public void update() {
		StickyChunk.getInstance().getDataStore().updateUser(this);
	}

	public ArrayList<Chunk> getChunks(World world) {
		ArrayList<Chunk> chunks = Lists.newArrayList();
		loadedChunks.values().forEach(chunkLoaderData ->
			chunks.addAll(chunkLoaderData.getChunks(world))
		);

		return chunks;
	}

	public ArrayList<Chunk> getChunks(String type, World world) {
		return loadedChunks.get(type).getChunks(world);
	}

	public ArrayList<Chunk> getCollatedChunks() {
		ArrayList<Chunk> chunks = Lists.newArrayList();
		loadedChunks.values().forEach(chunkLoaderData ->
			chunks.addAll(chunkLoaderData.getAllChunks())
		);

		return chunks;
	}
}
