/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GOLD;
import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.PLATINUM;
import static com.nephest.battlenet.sc2.model.QueueType.HOTS_1V1;
import static com.nephest.battlenet.sc2.model.QueueType.WOL_1V1;
import static com.nephest.battlenet.sc2.model.TeamType.ARRANGED;
import static com.nephest.battlenet.sc2.model.TeamType.RANDOM;

public class LeagueTest
{

    @Test
    public void testUniqueness()
    {
        League league = new League(0l, 0l, GOLD, WOL_1V1, ARRANGED);
        League equalLeague = new League(1l, 0l, GOLD, WOL_1V1, ARRANGED);

        League[] notEqualLeagues = new League[]
        {
            new League(0l, 1l, GOLD, WOL_1V1, ARRANGED),
            new League(0l, 0l, PLATINUM, WOL_1V1, ARRANGED),
            new League(0l, 0l, GOLD, HOTS_1V1, ARRANGED),
            new League(0l, 0l, GOLD, WOL_1V1, RANDOM),
        };

        TestUtil.testUniqueness(league, equalLeague, notEqualLeagues);
    }

}
