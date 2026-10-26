# Guide de Démonstration - LearnifyEnglish avec RAG

**Projet universitaire** : Plateforme d'apprentissage de l'anglais avec assistant pédagogique intelligent  
**Étudiant** : [Votre Nom]  
**Date** : Octobre 2026

---

## 📋 Table des matières

1. [Vue d'ensemble du projet](#vue-densemble-du-projet)
2. [Architecture technique](#architecture-technique)
3. [Résolution du problème Groq](#résolution-du-problème-groq)
4. [Implémentation RAG](#implémentation-rag)
5. [Configuration et installation](#configuration-et-installation)
6. [Scénario de démonstration](#scénario-de-démonstration)
7. [Tests et validation](#tests-et-validation)
8. [Différences : Chatbot initial vs Système RAG](#différences--chatbot-initial-vs-système-rag)

---

## 🎯 Vue d'ensemble du projet

### Objectifs pédagogiques

LearnifyEnglish est une plateforme d'apprentissage de l'anglais qui intègre :

- ✅ **LLM** : Groq API avec modèles open-source (GPT-OSS)
- ✅ **API REST** : Spring Boot microservices
- ✅ **Prompt Engineering** : Templates structurés et contextuels
- ✅ **RAG** : Retrieval-Augmented Generation avec base documentaire CECRL

### Fonctionnalités principales

1. **Préévaluation du niveau d'anglais** (A1, A2, B1, B2 selon CECRL)
2. **Détection des erreurs** et analyse détaillée des réponses incorrectes
3. **Assistant pédagogique intelligent** avec bouton "Ask AI"
4. **Chatbot conversationnel** pour approfondir les explications
5. **Base documentaire structurée** par niveau et catégorie grammaticale

---

## 🏗️ Architecture technique

### Structure du projet

```
merge_integ/
├── frontend/                    # Angular application
│   └── src/app/
│       └── student/preevaluation/
│           ├── result/          # Page des résultats avec "Ask AI"
│           └── chatbot/         # Interface conversationnelle
│
└── integrated/                  # Backend Spring Boot
    ├── preevaluation-service/   # Service principal (port 8089)
    │   ├── src/main/java/pi/integrated/preevaluation/
    │   │   ├── ai/              # Client Groq API
    │   │   │   ├── GroqChatClient.java
    │   │   │   ├── GroqApiSupport.java
    │   │   │   └── AiUserMessageMapper.java
    │   │   ├── rag/             # Système RAG
    │   │   │   ├── RagRetrievalService.java      # Recherche documentaire
    │   │   │   ├── RagVectorIndex.java           # Index vectoriel en mémoire
    │   │   │   ├── RagIndexingRunner.java        # Indexation au démarrage
    │   │   │   ├── RagPromptTemplates.java       # Templates de prompts
    │   │   │   ├── GroqEmbeddingClient.java      # Embeddings Groq
    │   │   │   ├── LocalHashEmbeddingService.java # Fallback local
    │   │   │   ├── KnowledgeBaseLoader.java      # Chargement JSON
    │   │   │   └── PedagogicalChunkBuilder.java  # Découpage intelligent
    │   │   └── PreevaluationAiExplanationService.java
    │   └── src/main/resources/
    │       ├── application.yml              # Configuration principale
    │       └── rag/knowledge/
    │           └── cefr_pedagogy.json       # Base documentaire
    │
    ├── api-gateway/             # Gateway (port 8765)
    └── eureka-server/           # Service discovery (port 8761)
```

### Flux de traitement d'une question "Ask AI"

```
┌─────────────┐
│   Étudiant  │ Clique sur "Ask AI" pour une erreur
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. RÉCUPÉRATION DU CONTEXTE (PreevaluationAiExplanation)   │
│    - Question posée                                          │
│    - Réponse de l'étudiant (incorrecte)                     │
│    - Réponse correcte                                        │
│    - Niveau CECRL (A1, A2, B1, B2)                          │
│    - Catégorie (GRAMMAR, VOCABULARY, etc.)                  │
└──────┬──────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. RECHERCHE DOCUMENTAIRE RAG (RagRetrievalService)        │
│    - Construction de la requête contextuelle                 │
│    - Génération d'embeddings (Groq ou local)                │
│    - Recherche par similarité cosinus                        │
│    - Filtrage par niveau et catégorie                        │
│    - Top-K passages pertinents (défaut: 4)                  │
└──────┬──────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. CONSTRUCTION DU PROMPT (RagPromptTemplates)             │
│    SYSTEM:                                                   │
│    - Rôle : tuteur d'anglais pédagogique                    │
│    - Niveau ciblé : adapté à l'étudiant                     │
│                                                              │
│    USER:                                                     │
│    - Contexte de l'erreur                                   │
│    - Extraits documentaires récupérés                       │
│    - Question spécifique de l'étudiant                      │
└──────┬──────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. GÉNÉRATION DE LA RÉPONSE (GroqChatClient)               │
│    - Appel API Groq Chat Completions                        │
│    - Modèle : openai/gpt-oss-20b (configurable)            │
│    - Température : 0.35 (précision)                         │
│    - Max tokens : 900                                        │
└──────┬──────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. AFFICHAGE À L'ÉTUDIANT                                   │
│    - Explication structurée et pédagogique                  │
│    - Références aux sources documentaires                   │
│    - Possibilité de poursuivre avec le chatbot              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 Résolution du problème Groq

### Problème initial

**Erreur affichée** : `The model 'llama-3.1-8b-instant' does not exist or you do not have access to it.`

### Diagnostic

1. **Modèle obsolète ou Enterprise** : Le modèle `llama-3.1-8b-instant` n'est plus disponible dans le plan Developer de Groq (nécessite Enterprise)
2. **Variable d'environnement manquante** : `GROQ_MODEL` n'était pas définie, causant l'utilisation d'une valeur obsolète ou incorrecte

### Solution implémentée

1. **Vérification de la documentation officielle Groq** (octobre 2026)
   - Consultation de `https://console.groq.com/docs/models`
   - Identification des modèles production disponibles

2. **Modèles recommandés pour Developer Plan** :
   - ✅ `openai/gpt-oss-20b` (rapide, 1000 T/sec, $0.075/1M tokens)
   - ✅ `openai/gpt-oss-120b` (puissant, 500 T/sec, $0.15/1M tokens)

3. **Configuration par défaut** dans `application.yml` :
   ```yaml
   preevaluation:
     ai:
       model: ${GROQ_MODEL:openai/gpt-oss-20b}
   ```

4. **Gestion des erreurs améliorée** :
   - Messages utilisateur clairs sans exposer les détails techniques
   - Logs sécurisés pour le débogage
   - Suggestions de configuration en cas d'erreur de modèle

### Fichiers modifiés

- ✅ `application.yml` : Modèle par défaut mis à jour
- ✅ `.env.example` : Documentation des variables d'environnement
- ✅ `GroqApiSupport.java` : Résolution correcte du modèle
- ✅ `AiUserMessageMapper.java` : Messages d'erreur pédagogiques

---

## 🧠 Implémentation RAG

### Qu'est-ce que le RAG ?

**RAG (Retrieval-Augmented Generation)** = Recherche documentaire + Génération par LLM

Au lieu d'envoyer directement la question au LLM, le système :
1. **Recherche** les passages pertinents dans une base documentaire
2. **Injecte** ces passages dans le prompt du LLM
3. **Génère** une réponse fondée sur ces sources fiables

### Avantages pour LearnifyEnglish

| Aspect | Sans RAG | Avec RAG |
|--------|----------|----------|
| **Sources** | Connaissances du modèle (potentiellement obsolètes) | Base documentaire CECRL vérifiée |
| **Précision** | Peut halluciner ou inventer des règles | Explications fondées sur des sources pédagogiques |
| **Adaptation** | Générique | Adapté au niveau CECRL de l'étudiant |
| **Cohérence** | Variable selon le modèle | Cohérent avec les standards d'enseignement |
| **Traçabilité** | Aucune | Références aux sources utilisées |

### Architecture RAG implémentée

#### A. Base de connaissances pédagogique

**Fichier** : `src/main/resources/rag/knowledge/cefr_pedagogy.json`

**Structure d'un document** :
```json
{
  "id": "a2-grammar-present-continuous",
  "levels": ["A2", "B1"],
  "category": "GRAMMAR",
  "ruleTitle": "Present continuous (am/is/are + -ing)",
  "rule": "Use am/is/are + verb-ing for actions happening now...",
  "correctExamples": ["I am reading a book right now."],
  "incorrectExamples": ["I read a book right now."],
  "explanation": "Time markers such as now, at the moment...",
  "keywords": ["present continuous", "now", "am reading", "-ing"],
  "exerciseHint": "I ___ a book right now. (read)",
  "sources": [{
    "label": "LearnifyEnglish — original pedagogical sheet (A2)",
    "type": "original"
  }]
}
```

**Contenu actuel** :
- ✅ 8+ documents couvrant A1, A2, B1, B2
- ✅ Catégories : GRAMMAR (verbes, temps, articles, comparatifs, etc.)
- ✅ Exemples corrects et incorrects pour chaque règle
- ✅ Explications adaptées au niveau
- ✅ Mots-clés pour la recherche
- ✅ Sources documentées

#### B. Pipeline RAG complet

**1. Chargement et indexation** (`RagIndexingRunner`)
- Exécution au démarrage du service (`@PostConstruct`)
- Lecture de tous les fichiers JSON dans `rag/knowledge/`
- Découpage en passages cohérents (`PedagogicalChunkBuilder`)
- Génération d'embeddings (Groq API ou local)
- Construction de l'index vectoriel en mémoire

**2. Génération d'embeddings** (2 stratégies)

a) **Groq Embeddings API** (`GroqEmbeddingClient`) - Préféré
   - Modèle : `nomic-embed-text-v1_5`
   - Vecteurs de haute qualité
   - Batch processing pour performance

b) **Embeddings locaux** (`LocalHashEmbeddingService`) - Fallback
   - Utilisé si Groq embeddings indisponible
   - TF (Term Frequency) avec hash
   - Pas de dépendance externe
   - Performances acceptables pour un prototype

**3. Recherche documentaire** (`RagRetrievalService`)

```java
public List<RetrievedPassage> retrieve(String apiKey, RagQueryContext query) {
    // 1. Construction de la requête combinée
    String combinedQuery = buildSearchQuery(query);
    
    // 2. Génération d'embedding pour la requête
    float[] queryVector = embeddingService.embed(apiKey, combinedQuery);
    
    // 3. Recherche par similarité cosinus
    List<RetrievedPassage> candidates = vectorIndex.search(queryVector, topK);
    
    // 4. Filtrage par niveau et catégorie
    candidates = filterByLevel(candidates, query.getStudentLevel());
    candidates = filterByCategory(candidates, query.getCategory());
    
    // 5. Score minimum de pertinence
    candidates = candidates.stream()
        .filter(p -> p.getScore() >= minScore)
        .collect(toList());
    
    return candidates;
}
```

**Paramètres configurables** (application.yml) :
```yaml
preevaluation:
  rag:
    enabled: true
    top-k: 4              # Nombre maximum de passages récupérés
    min-score: 0.12       # Seuil de pertinence minimum
    index-on-startup: true
```

**4. Templates de prompts** (`RagPromptTemplates`)

**Prompt système pour erreurs** :
```
You are an experienced English tutor helping a student at level {level}.
Your goal is to explain why their answer is incorrect and teach the correct rule.

Guidelines:
- Be encouraging and pedagogical
- Use simple language appropriate for {level}
- Reference the provided knowledge base excerpts
- Give concrete examples
- Provide a memorable tip to remember the rule
```

**Prompt utilisateur** :
```
Question: {questionText}
Student's answer: {studentAnswer}
Correct answer: {correctAnswer}
Category: {category}

Relevant knowledge base excerpts:
---
{passage1}
Source: {source1}
---
{passage2}
Source: {source2}
---

Please explain the mistake to the student.
```

**5. Détection d'intention** (`FollowUpIntent`)

Pour les conversations de suivi, le système détecte l'intention :

```java
public enum FollowUpIntent {
    MORE_EXPLANATION,    // "Explain more", "I don't understand"
    EXAMPLE,            // "Give me another example"
    SIMPLIFY,           // "Simpler please", "Too complex"
    EXERCISE,           // "Give me an exercise", "Practice"
    GENERAL             // Autres questions
}
```

Cela permet d'adapter :
- Le type de recherche documentaire
- La structure du prompt
- La température du modèle

#### C. Intégration avec le bouton "Ask AI"

**Endpoint** : `POST /api/preevaluation/ai/explain-mistake`

**Requête** :
```json
{
  "questionId": 123
}
```

**Processus** :
1. Vérification que l'étudiant a bien répondu incorrectement à cette question
2. Chargement du contexte complet (question, réponse étudiant, réponse correcte)
3. Construction d'une `RagQueryContext` avec niveau et catégorie
4. Recherche des 4 passages les plus pertinents
5. Construction du prompt avec les extraits
6. Appel Groq Chat API
7. Retour de l'explication + sources

**Réponse** :
```json
{
  "explanation": "Your answer 'read' uses the present simple, but the sentence needs the present continuous because of 'right now'...",
  "sources": [
    "LearnifyEnglish — original pedagogical sheet (A2)",
    "LearnifyEnglish — original pedagogical sheet (A2)"
  ]
}
```

#### D. Intégration avec le chatbot

**Endpoint** : `POST /api/preevaluation/ai/follow-up`

**Requête** :
```json
{
  "questionId": 123,
  "initialExplanation": "...",
  "priorMessages": [
    {"role": "user", "content": "Can you give me another example?"},
    {"role": "assistant", "content": "Sure! ..."}
  ],
  "userMessage": "What about with 'she'?"
}
```

**Améliorations** :
- ✅ Contexte conversationnel maintenu (max 10 messages précédents)
- ✅ Détection d'intention pour recherche ciblée
- ✅ Nouvelle recherche documentaire si nécessaire
- ✅ Adaptation au niveau de l'étudiant
- ✅ Validation de l'alternance user/assistant

---

## ⚙️ Configuration et installation

### Prérequis

- ☑️ Java 17+
- ☑️ Maven 3.6+
- ☑️ MySQL 8.0+
- ☑️ Node.js 18+ et npm (pour Angular)
- ☑️ Compte Groq (gratuit) : https://console.groq.com/

### Étape 1 : Obtenir une clé API Groq

1. Aller sur https://console.groq.com/keys
2. Se connecter ou créer un compte (gratuit)
3. Cliquer sur "Create API Key"
4. Copier la clé (format : `gsk_...`)

⚠️ **IMPORTANT** : Ne jamais committer cette clé dans Git !

### Étape 2 : Configuration du backend

**A. Créer le fichier `.env`** (à la racine du projet)

```bash
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\integrated\preevaluation-service
copy .env.example .env
```

**B. Éditer `.env` avec vos valeurs** :

```properties
GROQ_API_KEY=gsk_votre_vraie_cle_ici
GROQ_MODEL=openai/gpt-oss-20b
GROQ_EMBEDDING_MODEL=nomic-embed-text-v1_5

SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/preevaluation_db?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=votre_mot_de_passe
```

**C. Créer la base de données MySQL**

```sql
CREATE DATABASE IF NOT EXISTS preevaluation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Le schéma sera créé automatiquement au démarrage (JPA `ddl-auto: update`).

### Étape 3 : Démarrage des services

**Option A : Démarrage complet avec Eureka** (recommandé pour production)

```powershell
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\integrated

# 1. Eureka Server
cd eureka-server
mvn spring-boot:run

# 2. API Gateway (nouveau terminal)
cd ..\api-gateway
mvn spring-boot:run

# 3. Preevaluation Service (nouveau terminal)
cd ..\preevaluation-service
mvn spring-boot:run
```

**Option B : Démarrage rapide sans Eureka** (développement local)

```powershell
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\integrated\preevaluation-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Vérification des services** :
- Eureka : http://localhost:8761
- API Gateway : http://localhost:8765
- Preevaluation Service : http://localhost:8089/actuator/health

**Logs de démarrage RAG** :

Vous devriez voir dans les logs :
```
INFO  RagIndexingRunner - RAG indexing started
INFO  KnowledgeBaseLoader - Loaded 8 knowledge documents
INFO  PedagogicalChunkBuilder - Created 24 passages
INFO  GroqEmbeddingClient - Generated 24 embeddings (3 batches)
INFO  RagVectorIndex - Index built with 24 passages
INFO  RagIndexingRunner - RAG indexing completed in 1.2s
```

Si les embeddings Groq ne sont pas disponibles :
```
WARN  GroqEmbeddingClient - Groq embeddings unavailable, using local fallback
INFO  LocalHashEmbeddingService - Using TF-based local embeddings
```

### Étape 4 : Démarrage du frontend Angular

```powershell
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\frontend
npm install
npm start
```

Application disponible sur : http://localhost:4200

---

## 🎬 Scénario de démonstration

### Préparation (5 min)

1. ✅ Tous les services démarrés
2. ✅ Un compte étudiant créé (ou utiliser l'existant)
3. ✅ Navigateur ouvert sur http://localhost:4200

### Démonstration (15-20 min)

#### Partie 1 : Préévaluation du niveau (5 min)

1. **Connexion** en tant qu'étudiant
2. **Démarrer la préévaluation**
3. **Répondre volontairement à quelques questions de manière incorrecte** :
   
   Exemple ciblé pour le RAG :
   - Question A2 : `I ___ a book right now.`
   - ✅ Bonne réponse : `am reading`
   - ❌ Choisir : `read` (pour tester l'explication du présent continu)

4. **Terminer le test** et voir le niveau attribué

#### Partie 2 : Assistant pédagogique avec RAG (5-7 min)

1. **Page des résultats** : liste des erreurs commises
2. **Cliquer sur "Ask AI"** pour la question du présent continu
3. **Observer l'explication générée** :

   L'assistant devrait expliquer :
   - ✅ Pourquoi "read" est incorrect (présent simple)
   - ✅ Pourquoi "am reading" est correct (action en cours)
   - ✅ La règle du présent continu (am/is/are + -ing)
   - ✅ Des exemples adaptés au niveau A2
   - ✅ Un conseil pour retenir la règle

4. **Noter les sources affichées** : "LearnifyEnglish — original pedagogical sheet (A2)"

#### Partie 3 : Chatbot conversationnel (5-7 min)

1. **Poursuivre la conversation** :
   - Taper : "Give me another example"
   - Observer : nouvelle recherche RAG + exemple additionnel

2. **Test de simplification** :
   - Taper : "Explain it more simply"
   - Observer : explication reformulée pour débutant

3. **Demande d'exercice** :
   - Taper : "Give me an exercise to practice"
   - Observer : exercice généré adapté au niveau

4. **Question de suivi** :
   - Taper : "What about with 'she'?"
   - Observer : maintien du contexte + adaptation de l'exemple

#### Partie 4 : Comparaison technique (3 min)

**Montrer dans le code** :

1. **Base documentaire** :
   ```
   integrated/preevaluation-service/src/main/resources/rag/knowledge/cefr_pedagogy.json
   ```
   - Montrer la structure d'un document
   - Expliquer les niveaux CECRL
   - Montrer les sources

2. **Service RAG** :
   ```
   integrated/preevaluation-service/src/main/java/pi/integrated/preevaluation/rag/
   ```
   - `RagRetrievalService.java` : recherche documentaire
   - `RagPromptTemplates.java` : construction des prompts

3. **Configuration** :
   ```
   integrated/preevaluation-service/src/main/resources/application.yml
   ```
   - Variables d'environnement
   - Paramètres RAG

---

## ✅ Tests et validation

### Tests automatisés

**Commande** :
```powershell
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\integrated\preevaluation-service
mvn test
```

**Tests implémentés** :

| Test | Fichier | Vérifie |
|------|---------|---------|
| Chargement de la base documentaire | `KnowledgeBaseLoaderTest` | Lecture des JSON, structure valide |
| Construction de passages | `PedagogicalChunkBuilderTest` | Découpage intelligent des documents |
| Embeddings locaux | `LocalHashEmbeddingServiceTest` | Génération de vecteurs fallback |
| Index vectoriel | `RagVectorIndexTest` | Recherche par similarité, filtrage |
| Détection d'intention | `FollowUpIntentTest` | Classification des demandes de suivi |
| Messages d'erreur | `AiUserMessageMapperTest` | Conversion des erreurs Groq |
| Templates de prompts | `RagPromptTemplatesTest` | Construction correcte des prompts |

**Résultats attendus** :
```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Tests manuels recommandés

#### Test 1 : Erreur grammaticale A2 (présent continu)

**Question** : `I ___ a book right now.`  
**Réponse incorrecte** : `read`  
**Réponse correcte** : `am reading`

**Vérifications** :
- ✅ Explication mentionne "present continuous"
- ✅ Explication cite "right now" comme indicateur temporel
- ✅ Exemples donnés sont corrects
- ✅ Source référencée : "LearnifyEnglish — A2"
- ✅ Pas d'hallucination de règles

#### Test 2 : Erreur grammaticale A1 (verbe be)

**Question** : `She ___ happy.`  
**Réponse incorrecte** : `are`  
**Réponse correcte** : `is`

**Vérifications** :
- ✅ Explication de l'accord sujet-verbe
- ✅ Règle "is avec he/she/it"
- ✅ Exemples adaptés au niveau A1
- ✅ Langage simple pour débutant

#### Test 3 : Conversation de suivi

**Séquence** :
1. Ask AI sur une erreur
2. "Give me another example" → vérifie nouveau exemple
3. "Explain more" → vérifie approfondissement
4. "Give me an exercise" → vérifie génération d'exercice

**Vérifications** :
- ✅ Contexte maintenu sur toute la conversation
- ✅ Pas de répétition mot pour mot
- ✅ Adaptation au fil de la discussion
- ✅ Cohérence avec l'explication initiale

#### Test 4 : Gestion d'erreur Groq

**Procédure** :
1. Arrêter le service
2. Définir `GROQ_API_KEY=invalid_key`
3. Redémarrer le service
4. Tenter "Ask AI"

**Vérifications** :
- ✅ Message d'erreur convivial (pas de stack trace)
- ✅ Suggestion de configurer `GROQ_API_KEY`
- ✅ Logs détaillés côté serveur uniquement
- ✅ Clé invalide jamais exposée au navigateur

#### Test 5 : Fallback embeddings locaux

**Procédure** :
1. Définir `GROQ_EMBEDDING_MODEL=invalid_model`
2. Redémarrer le service
3. Observer les logs

**Vérifications** :
- ✅ Log WARN : "Groq embeddings unavailable, using local fallback"
- ✅ Indexation complète avec LocalHashEmbeddingService
- ✅ Ask AI fonctionne (avec qualité réduite acceptable)

### Comparaison avant/après RAG

**Test comparatif** (si possible avec un backup de l'ancienne version) :

| Critère | Sans RAG | Avec RAG |
|---------|----------|----------|
| **Temps de réponse** | ~2-3s | ~3-4s (recherche + génération) |
| **Précision des règles** | Variable | Fondée sur la base documentaire |
| **Adaptation au niveau** | Générique | Ciblée A1/A2/B1/B2 |
| **Sources** | Aucune | Références affichées |
| **Cohérence** | Dépend du prompt | Constante avec la base |
| **Exemples** | Parfois hors niveau | Adaptés au CECRL |
| **Hallucinations** | Possibles | Réduites (ancrées dans les docs) |

---

## 🆚 Différences : Chatbot initial vs Système RAG

### Chatbot initial (hypothétique sans RAG)

**Fonctionnement** :
```
Étudiant → Erreur détectée → Prompt générique → Groq API → Réponse
```

**Prompt typique** :
```
You are an English tutor. The student answered "read" instead of "am reading". 
Explain the mistake.
```

**Limitations** :
- ❌ Pas de garantie que l'explication suit les standards CECRL
- ❌ Peut inventer des règles ou donner des exemples hors niveau
- ❌ Aucune traçabilité des sources
- ❌ Pas d'adaptation fine au niveau de l'étudiant
- ❌ Cohérence variable entre les explications

### Système RAG implémenté

**Fonctionnement** :
```
Étudiant → Erreur détectée
    ↓
Recherche documentaire (4 passages pertinents)
    ↓
Prompt enrichi avec extraits + contexte
    ↓
Groq API avec base documentaire
    ↓
Réponse fondée sur sources fiables
```

**Prompt enrichi** :
```
SYSTEM: You are an English tutor for level A2. Use the provided knowledge base excerpts.

USER: 
Question: I ___ a book right now.
Student's answer: read
Correct answer: am reading
Category: GRAMMAR

Knowledge base excerpts:
---
Rule: Present continuous (am/is/are + -ing)
Use am/is/are + verb-ing for actions happening now...
Examples: I am reading a book right now.
Source: LearnifyEnglish — original pedagogical sheet (A2)
---

Explain the mistake.
```

**Avantages** :
- ✅ Explications ancrées dans une base documentaire vérifiée
- ✅ Adaptation précise au niveau CECRL
- ✅ Sources traçables et affichées
- ✅ Cohérence garantie avec les standards pédagogiques
- ✅ Réduction des hallucinations
- ✅ Possibilité d'enrichir facilement la base de connaissances
- ✅ Recherche intelligente avec filtrage par niveau et catégorie

### Métaphore pédagogique

**Sans RAG** : Comme un tuteur qui répond de mémoire, sans livre de référence. Il peut être brillant, mais aussi se tromper ou donner des explications incohérentes.

**Avec RAG** : Comme un tuteur qui consulte d'abord un manuel pédagogique CECRL avant de répondre. Il fonde ses explications sur des sources fiables et peut citer ses références.

---

## 📚 Ressources et documentation

### Documentation officielle

- **Groq API** : https://console.groq.com/docs
- **Modèles disponibles** : https://console.groq.com/docs/models
- **Spring Boot** : https://spring.io/projects/spring-boot
- **Angular** : https://angular.io/

### Fichiers clés du projet

| Fichier | Description |
|---------|-------------|
| `RAG.md` | Documentation technique du système RAG |
| `.env.example` | Modèle de configuration |
| `application.yml` | Configuration Spring Boot |
| `cefr_pedagogy.json` | Base documentaire pédagogique |
| `GUIDE_DEMONSTRATION_PROFESSEURE.md` | Ce document |

### Commandes utiles

```powershell
# Démarrage rapide sans Eureka
cd integrated\preevaluation-service
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Tests
mvn test

# Vérifier les logs
Get-Content logs\preevaluation-service.log -Tail 50

# Frontend Angular
cd frontend
npm start
```

---

## 🎓 Conclusion

Ce projet démontre l'intégration complète d'un système RAG dans une application pédagogique :

1. ✅ **LLM** : Groq API avec modèles open-source performants
2. ✅ **API REST** : Architecture microservices Spring Boot
3. ✅ **Prompt Engineering** : Templates structurés et contextuels
4. ✅ **RAG** : Recherche documentaire + génération fondée sur sources fiables

Le système combine la puissance des LLM avec la fiabilité d'une base documentaire pédagogique, offrant ainsi des explications précises, adaptées au niveau CECRL, et traçables.

---

**Questions pour la démonstration ?**

1. Voulez-vous que je montre d'abord l'interface utilisateur ou l'architecture technique ?
2. Y a-t-il un aspect particulier que vous souhaitez approfondir ?
3. Souhaitez-vous voir les logs de recherche RAG en temps réel ?
4. Voulez-vous que je teste volontairement une erreur pour montrer la gestion des exceptions ?

---

*Document préparé pour la démonstration universitaire - Octobre 2026*
