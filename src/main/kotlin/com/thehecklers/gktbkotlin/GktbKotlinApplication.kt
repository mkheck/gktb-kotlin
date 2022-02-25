package com.thehecklers.gktbkotlin

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux

@SpringBootApplication
class GktbKotlinApplication {
    @Bean
    fun client() = WebClient.create("http://localhost:9876/metar")

    @Bean
    fun loadData(repo: AirportRepository) = CommandLineRunner {
        repo.deleteAll()
            .thenMany(
                Flux.just(
                    Airport("KGAG", "Gage Airport"),
                    Airport("KLOL", "Derby Field"),
                    Airport("KBUM", "Butler Memorial Airport"),
                    Airport("KSTL", "St. Louis Lambert International Airport"),
                    Airport("KORD", "O'Hare International Airport")
                )
            )
            .flatMap { repo.save(it) }
            .log()
            .subscribe()
    }

    @Bean
    fun routerFunction(svc: AirportInfoService) = router {
        accept(APPLICATION_JSON).nest {
            GET("/", svc::allAirports)
            GET("/{id}", svc::airportById)
            GET("/metar/{id}", svc::metar)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<GktbKotlinApplication>(*args)
}

@Service
class AirportInfoService(
    private val repo: AirportRepository,
    private val wxClient: WebClient
) {
    fun allAirports(req: ServerRequest) = ok().body<Airport>(repo.findAll())

    fun airportById(req: ServerRequest) = ok().body<Airport>(repo.findById(req.pathVariable("id")))

    fun metar(req: ServerRequest) = ok()
        .body<METAR>(
            wxClient.get()
                .uri("?loc=${req.pathVariable("id")}")
                .retrieve()
                .bodyToMono<METAR>()
        )
}

interface AirportRepository : ReactiveCrudRepository<Airport, String>

@Document
data class Airport(@Id val id: String, val name: String)

data class METAR(val flight_rules: String, val raw: String)