# RAG — assistant pédagogique (preevaluation-service)

## Architecture

1. **Base documentaire** : `src/main/resources/rag/knowledge/*.json` (fiches CECRL A1–B2, contenus LearnifyEnglish originaux).
2. **Indexation au démarrage** (`RagIndexingRunner`) : chargement JSON → passages (`PedagogicalChunkBuilder`) → embeddings → index mémoire (`RagVectorIndex`).
3. **Embeddings** : API Groq `GROQ_EMBEDDING_MODEL` si disponible ; sinon **vecteurs locaux** (hash TF) documentés dans les logs.
4. **Recherche** (`RagRetrievalService`) : similarité cosinus + filtre niveau/catégorie + repli sans catégorie.
5. **Génération** (`GroqChatClient` + `RagPromptTemplates`) : Groq chat avec extraits injectés dans le prompt.
6. **Endpoints existants** : `POST /api/preevaluation/ai/explain-mistake`, `POST /api/preevaluation/ai/follow-up`.

## Variables d'environnement

| Variable | Rôle |
|----------|------|
| `GROQ_API_KEY` | Clé API (côté serveur uniquement) |
| `GROQ_MODEL` | Modèle de chat (défaut : `openai/gpt-oss-20b`) |
| `GROQ_EMBEDDING_MODEL` | Modèle d'embeddings (défaut : `nomic-embed-text-v1_5`) |

Propriétés Spring : `preevaluation.ai.*`, `preevaluation.rag.*` dans `application.yml`.

## Préparer / ré-indexer les documents

1. Ajouter ou modifier des entrées dans `rag/knowledge/cefr_pedagogy.json`.
2. Redémarrer `preevaluation-service` (index reconstruit si `preevaluation.rag.index-on-startup=true`).

## Lancer localement

```bash
cd integrated/preevaluation-service
set GROQ_API_KEY=votre_cle
set GROQ_MODEL=openai/gpt-oss-20b
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Front Angular : s'assurer que le gateway ou le proxy cible le port **8089**.

## Tests

```bash
mvn -q test
```
