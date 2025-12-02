package com.example.demobase.service;

import com.example.demobase.dto.GameDTO;
import com.example.demobase.dto.GameResponseDTO;
import com.example.demobase.model.Game;
import com.example.demobase.model.GameInProgress;
import com.example.demobase.model.Player;
import com.example.demobase.model.Word;
import com.example.demobase.repository.GameInProgressRepository;
import com.example.demobase.repository.GameRepository;
import com.example.demobase.repository.PlayerRepository;
import com.example.demobase.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final GameInProgressRepository gameInProgressRepository;
    private final PlayerRepository playerRepository;
    private final WordRepository wordRepository;
    private final ModelMapper modelMapper;

    private static final int MAX_INTENTOS = 7;
    private static final int PUNTOS_PALABRA_COMPLETA = 20;
    private static final int PUNTOS_POR_LETRA = 1;


    @Transactional
    public GameResponseDTO startGame(Long playerId) {

        // Validar si el jugador existe
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado"));

        // Seleccionar palabra aleatoria no utilizada
        Word word = wordRepository.findRandomWord()
                .orElseThrow(() -> new RuntimeException("No quedan palabras disponibles"));

        // Marcar palabra como utilizada
        word.setUtilizada(true);
        wordRepository.save(word);

        // Crear nueva partida en curso
        GameInProgress gip = new GameInProgress();
        gip.setJugador(player);
        gip.setPalabra(word);
        gip.setLetrasIntentadas("");   // Arranca vacío
        gip.setIntentosRestantes(MAX_INTENTOS);
        gip.setFechaInicio(LocalDateTime.now());

        gameInProgressRepository.save(gip);

        // Construir respuesta formateada
        return buildResponseFromGameInProgress(gip);
    }



    @Transactional
    public GameResponseDTO makeGuess(Long playerId, Character letra) {

        // Validar jugador
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado"));

        letra = Character.toUpperCase(letra);

        // Buscar la partida en curso más reciente
        List<GameInProgress> lista = gameInProgressRepository
                .findByJugadorIdOrderByFechaInicioDesc(playerId);

        if (lista.isEmpty()) {
            throw new RuntimeException("No hay partida en curso");
        }

        GameInProgress gip = lista.get(0); // La más reciente

        // Convertir letras guardadas en Set<Character>
        Set<Character> letras = stringToCharSet(gip.getLetrasIntentadas());

        // Si la letra ya fue intentada → devolver mismo estado
        if (letras.contains(letra)) {
            return buildResponseFromGameInProgress(gip);
        }

        // Agregar nueva letra intentada
        letras.add(letra);

        // Verificar si la letra está en la palabra
        String palabra = gip.getPalabra().getPalabra().toUpperCase();
        boolean letraCorrecta = palabra.indexOf(letra) >= 0;

        // Restar un intento si es incorrecta
        if (!letraCorrecta) {
            gip.setIntentosRestantes(gip.getIntentosRestantes() - 1);
        }

        // Guardar letras actualizadas
        gip.setLetrasIntentadas(charSetToString(letras));

        // Generar palabra oculta
        String palabraOculta = generateHiddenWord(palabra, letras);
        boolean completa = palabraOculta.equals(palabra);

        // Si la partida terminó → guardar Game y eliminar GameInProgress
        if (completa || gip.getIntentosRestantes() == 0) {

            int puntaje = calculateScore(palabra, letras, completa, gip.getIntentosRestantes());

            saveGame(player, gip.getPalabra(), completa, puntaje);

            gameInProgressRepository.delete(gip);

            // Devolver resultado final
            GameResponseDTO r = new GameResponseDTO();
            r.setPalabraOculta(palabra);
            r.setLetrasIntentadas(new ArrayList<>(letras));
            r.setIntentosRestantes(gip.getIntentosRestantes());
            r.setPalabraCompleta(completa);
            r.setPuntajeAcumulado(puntaje);
            return r;
        }

        // Si todavía sigue → guardar progreso
        gameInProgressRepository.save(gip);

        // Construir respuesta intermedia
        return buildResponseFromGameInProgress(gip);
    }


    private GameResponseDTO buildResponseFromGameInProgress(GameInProgress gip) {
        String palabra = gip.getPalabra().getPalabra().toUpperCase();
        Set<Character> letras = stringToCharSet(gip.getLetrasIntentadas());
        String oculta = generateHiddenWord(palabra, letras);
        boolean completa = oculta.equals(palabra);

        GameResponseDTO response = new GameResponseDTO();
        response.setPalabraOculta(oculta);
        response.setLetrasIntentadas(new ArrayList<>(letras));
        response.setIntentosRestantes(gip.getIntentosRestantes());
        response.setPalabraCompleta(completa);

        int puntaje = calculateScore(palabra, letras, completa, gip.getIntentosRestantes());
        response.setPuntajeAcumulado(puntaje);

        return response;
    }

    private Set<Character> stringToCharSet(String str) {
        Set<Character> set = new HashSet<>();
        if (str != null && !str.isEmpty()) {
            String[] chars = str.split(",");
            for (String c : chars) {
                if (!c.trim().isEmpty()) {
                    set.add(c.trim().charAt(0));
                }
            }
        }
        return set;
    }

    private String charSetToString(Set<Character> set) {
        if (set == null || set.isEmpty()) return "";
        return set.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private int calculateScore(String palabra, Set<Character> letrasIntentadas,
                               boolean completa, int intentosRestantes) {

        if (completa) return PUNTOS_PALABRA_COMPLETA;

        if (intentosRestantes == 0) {
            long correctas = letrasIntentadas.stream()
                    .filter(l -> palabra.indexOf(l) >= 0)
                    .count();
            return (int) (correctas * PUNTOS_POR_LETRA);
        }

        return 0;
    }

    private String generateHiddenWord(String palabra, Set<Character> intentadas) {
        StringBuilder sb = new StringBuilder();
        for (char c : palabra.toCharArray()) {
            if (intentadas.contains(c)) sb.append(c);
            else sb.append('_');
        }
        return sb.toString();
    }

    @Transactional
    private void saveGame(Player player, Word word, boolean ganado, int puntaje) {

        if (!word.getUtilizada()) {
            word.setUtilizada(true);
            wordRepository.save(word);
        }

        Game game = new Game();
        game.setJugador(player);
        game.setPalabra(word);
        game.setResultado(ganado ? "GANADO" : "PERDIDO");
        game.setPuntaje(puntaje);
        game.setFechaPartida(LocalDateTime.now());

        gameRepository.save(game);
    }


    public List<GameDTO> getGamesByPlayer(Long playerId) {
        return gameRepository.findByJugadorId(playerId).stream()
                .map(game -> modelMapper.map(game, GameDTO.class))
                .collect(Collectors.toList());
    }

    public List<GameDTO> getAllGames() {
        return gameRepository.findAll().stream()
                .map(game -> modelMapper.map(game, GameDTO.class))
                .collect(Collectors.toList());
    }
}

