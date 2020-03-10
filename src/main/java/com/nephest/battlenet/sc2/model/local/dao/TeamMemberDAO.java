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
package com.nephest.battlenet.sc2.model.local.dao;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.core.convert.*;

import com.nephest.battlenet.sc2.model.local.*;

@Repository
public class TeamMemberDAO
{

    private static final String MERGE_QUERY = "INSERT INTO team_member "
        + "(team_id, player_character_id, terran_games_played, protoss_games_played, zerg_games_played, random_games_played) "
        + "VALUES (:teamId, :playerCharacterId, :terranGamesPlayed, :protossGamesPlayed, :zergGamesPlayed, :randomGamesPlayed) "

        + "ON DUPLICATE KEY UPDATE "
        + "team_id=VALUES(team_id), "
        + "player_character_id=VALUES(player_character_id), "
        + "terran_games_played=VALUES(terran_games_played), "
        + "protoss_games_played=VALUES(protoss_games_played), "
        + "zerg_games_played=VALUES(zerg_games_played), "
        + "random_games_played=VALUES(random_games_played)";

    private NamedParameterJdbcTemplate template;

    @Autowired
    public TeamMemberDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public TeamMember merge(TeamMember member)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("teamId", member.getTeamId())
            .addValue("playerCharacterId", member.getCharacterId())
            .addValue("terranGamesPlayed", member.getTerranGamesPlayed())
            .addValue("protossGamesPlayed", member.getProtossGamesPlayed())
            .addValue("zergGamesPlayed", member.getZergGamesPlayed())
            .addValue("randomGamesPlayed", member.getRandomGamesPlayed());
        template.update(MERGE_QUERY, params);
        return member;
    }

}
