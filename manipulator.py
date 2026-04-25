import pandas as pd

class Jogador:
    def __init__(self, Player, Age, Team, Pos, Rank):
        self.Player = Player
        self.Age = Age
        self.Team = Team
        self.Pos = Pos
        self.Rank = Rank

    def exibir_jogador(self):
        print(f"{self.Player:>25} | {self.Pos}")
        

class Time:
    def __init__(self, nome, elenco):
        self.nome = nome
        self.elenco = []

        self.elenco = [Jogador(r.Player, r.Age, r.Team, r.Pos, r.Rank) for r in elenco.itertuples()]

    def exibir_time(self):
        print(f"\n{self.nome}")
        for n in self.elenco:
            n.exibir_jogador()


def escolher_times(nba):
    t1, t2 = pd.Series(nba["Team"].unique()).sample(2)

    elenco1 = nba[nba["Team"] == t1]
    elenco2 = nba[nba["Team"] == t2]
    #print(f"{t1} X {t2}")

    t1 = Time(t1, elenco1)
    t2 = Time(t2, elenco2)

    return t1, t2

def exibir_partida(time1, time2):
    print(f"PARTIDA DE HOJE:{' '*2}{time1.nome} X {time2.nome}")
    time1.exibir_time()
    time2.exibir_time()
