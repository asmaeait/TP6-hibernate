# TP 6 — Optimistic Locking avec @Version

> **Technologie :** Java 8 · Hibernate 5.6 · JPA · H2 Database · IntelliJ IDEA  
> **Objectif :** Simuler un conflit de réservation concurrent et démontrer le verrouillage optimiste avec `@Version`

---

## 📋 Table des matières

- [Concept](#concept)
- [Structure du projet](#structure-du-projet)
- [Entités](#entités)
- [Service](#service)
- [Simulation de conflit](#simulation-de-conflit)
- [Gestion des conflits avec Retry](#gestion-des-conflits-avec-retry)
- [Résultats](#résultats)
- [Technologies utilisées](#technologies-utilisées)

---

## Concept

L'**Optimistic Locking** (verrouillage optimiste) est une stratégie de gestion de concurrence qui part du principe que les conflits sont rares. Au lieu de verrouiller les données dès le départ, il vérifie au moment de la sauvegarde si les données ont été modifiées entre-temps.

```
SANS @Version (problème)        AVEC @Version (solution)
────────────────────────        ────────────────────────
Jean  lit  → version=0          Jean  lit  → version=0
Sophie lit → version=0          Sophie lit → version=0
Sophie écrit → OK             Sophie écrit → OK, version=1 
Jean  écrit → OK              Jean  écrit →  CONFLIT détecté !
→ modification de Sophie        → Jean relit version=1, réessaie → 
  PERDUE définitivement !       → aucune modification perdue !
```

### Comment fonctionne `@Version` ?

```java
@Version
private Long version;
```

- À la **création** : version initialisée à `0`
- À chaque **UPDATE** : Hibernate incrémente la version automatiquement
- En cas de **conflit** : `OptimisticLockException` est levée

---

## Structure du projet


<img width="322" height="376" alt="structeur-projet" src="https://github.com/user-attachments/assets/5c317d24-ecb6-4837-94f4-f64a11feae0b" />


---

## Entités

### Relations entre entités

| Entité | Relation | Entité |
|--------|----------|--------|
| Reservation | `@ManyToOne` | Utilisateur |
| Reservation | `@ManyToOne` | Salle |

### `Reservation.java` — le coeur du TP

```java
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String motif;

    @ManyToOne
    private Utilisateur utilisateur;

    @ManyToOne
    private Salle salle;

    //  Clé de l'optimistic locking
    @Version
    private Long version;
}
```

---

## Service

`ReservationService` expose 4 méthodes :

| Méthode | Description |
|---------|-------------|
| `save(reservation)` | Persiste une nouvelle réservation |
| `findById(id)` | Récupère une réservation par son ID |
| `update(reservation)` | Met à jour — peut lever `OptimisticLockException` |
| `delete(reservation)` | Supprime une réservation |

---

## Simulation de conflit

`ConcurrentReservationSimulator` crée **2 threads** qui lisent la même réservation simultanément via `CountDownLatch` :

```
Thread 1 (Jean)    lit version=0 → attend 1 seconde → essaie d'écrire →  CONFLIT
Thread 2 (Sophie)  lit version=0 → écrit immédiatement →  version devient 1
```

Le `CountDownLatch` sert à faire démarrer les deux threads **exactement en même temps**.

### Screenshots
<img width="884" height="267" alt="1" src="https://github.com/user-attachments/assets/b2ff47e5-0fe6-46e2-a4b1-d9f580221062" />

<img width="922" height="263" alt="2" src="https://github.com/user-attachments/assets/8dd0802b-d1b9-42e0-b303-241218adcee3" />

---

## Gestion des conflits avec Retry

`OptimisticLockingRetryHandler` réessaie automatiquement l'opération en cas de conflit :

```
Tentative 1 : version=0 → CONFLIT → attendre 100ms
Tentative 2 : version=1 →  succès !
```

- Maximum **3 tentatives** configurables
- **Backoff** : attente de `100ms × numéro de tentative` entre chaque essai
- Si toutes les tentatives échouent → exception levée

---

## Résultats

### Sans retry
```
Thread 1 : Réservation récupérée, version = 0
Thread 2 : Réservation récupérée, version = 0
Thread 2 : Réservation mise à jour avec succès !
Thread 1 : Conflit de verrouillage optimiste détecté !

État final :
Motif      : Réunion d'équipe
Date début : ...+1h
Version    : 1
```

### Avec retry
```
Thread 1 : Modification du motif
Thread 2 : Modification des dates
Tentative 1 : version=0 →  Conflit pour Thread 1
Tentative 2 : version=1 →  Thread 1 réussit !

État final :
Motif      : Réunion d'équipe modifiée par Thread 1
Date début : ...+1h
Version    : 2
```

---

## Technologies utilisées

| Technologie | Version | Rôle |
|-------------|---------|------|
| Java | 1.8 | Langage |
| Hibernate ORM | 5.6.5 | Implémentation JPA |
| JPA | 2.2 | API de persistance |
| H2 Database | 2.1.214 | Base de données en mémoire |
| SLF4J | 1.7.36 | Logs |
| Maven | - | Gestion des dépendances |

---

Auteur : Asma Ait Elmahjoub
