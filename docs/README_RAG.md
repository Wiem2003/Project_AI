# LearnifyEnglish - Assistant Pédagogique avec RAG

🎓 Projet universitaire d'intégration LLM + RAG pour l'apprentissage de l'anglais

---

## 🚀 Démarrage rapide (5 minutes)

### Étape 1 : Obtenir une clé API Groq (gratuit)

1. Aller sur https://console.groq.com/keys
2. Se connecter ou créer un compte
3. Cliquer sur "Create API Key"
4. Copier la clé (format : `gsk_...`)

### Étape 2 : Configuration

```powershell
# Naviguer vers le service
cd integrated\preevaluation-service

# Créer le fichier .env depuis le modèle
copy .env.example .env

# Éditer .env et remplacer "votre_cle_ici" par votre vraie clé Groq
notepad .env
```

**Contenu minimal du `.env`** :
```properties
GROQ_API_KEY=gsk_votre_vraie_cle_ici
GROQ_MODEL=openai/gpt-oss-20b
```

### Étape 3 : Base de données

Créer la base MySQL :
```sql
CREATE DATABASE preevaluation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Étape 4 : Démarrage

**Backend** :
```powershell
cd integrated\preevaluation-service
.\start-local.ps1
```

Attendre le message : `RAG indexing completed in X.Xs`

**Frontend** (nouveau terminal) :
```powershell
cd frontend
npm install  # Première fois seulement
npm start
```

### Étape 5 : Tester

1. Ouvrir http://localhost:4200
2. Se connecter en tant qu'étudiant
3. Faire la préévaluation (répondre volontairement faux à quelques questions)
4. Cliquer sur "Ask AI" pour voir l'explication RAG
5. Utiliser le chatbot pour approfondir

---

## 📚 Documentation complète

- **Guide de démonstration** : `GUIDE_DEMONSTRATION_PROFESSEURE.md`
- **Résumé technique** : `IMPLEMENTATION_SUMMARY.md`
- **Documentation RAG** : `integrated/preevaluation-service/RAG.md`

---

## ✅ Vérification rapide

### Le service démarre correctement ?

**Logs attendus** :
```
INFO RagIndexingRunner - RAG indexing started
INFO KnowledgeBaseLoader - Loaded 8 knowledge documents
INFO PedagogicalChunkBuilder - Created 24 passages
INFO RagVectorIndex - Index built with 24 passages
INFO RagIndexingRunner - RAG indexing completed in 1.2s
```

### Les endpoints fonctionnent ?

- Health check : http://localhost:8089/actuator/health
- Frontend : http://localhost:4200

### Tests passent ?

```powershell
cd integrated\preevaluation-service
mvn test
```

Résultat attendu : `Tests run: 15, Failures: 0, Errors: 0`

---

## 🛠️ Résolution de problèmes

### Erreur : "The model 'llama-3.1-8b-instant' does not exist"

**Solution** : Le modèle est obsolète ou Enterprise.

Vérifier que `.env` contient :
```properties
GROQ_MODEL=openai/gpt-oss-20b
```

Si le fichier `.env` existe mais que l'erreur persiste, redémarrer le service.

### Erreur : "AI explanations are not configured"

**Cause** : `GROQ_API_KEY` n'est pas définie ou invalide.

**Solution** :
1. Vérifier que `.env` contient une vraie clé (commence par `gsk_`)
2. Vérifier qu'il n'y a pas d'espace avant ou après la clé
3. Redémarrer le service après modification

### Erreur : "Groq embeddings unavailable"

**Pas une erreur critique !** Le système utilise automatiquement un fallback local (embeddings TF-hash).

La qualité est légèrement réduite mais le système fonctionne.

Pour utiliser les embeddings Groq, vérifier que votre compte a accès au modèle `nomic-embed-text-v1_5`.

### Base de données non accessible

**Vérifier MySQL** :
```powershell
# MySQL est démarré ?
Get-Service MySQL*

# Base existe ?
mysql -u root -p
> SHOW DATABASES;
> USE preevaluation_db;
```

**Adapter la configuration** dans `.env` :
```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/preevaluation_db?useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=votre_mot_de_passe
```

---

## 🎯 Scénario de test recommandé

### Test du présent continu (A2)

1. **Faire la préévaluation**
2. **Question** : `I ___ a book right now.`
3. **Répondre volontairement** : `read` (au lieu de `am reading`)
4. **Terminer le test**
5. **Cliquer sur "Ask AI"** pour cette question

**Résultat attendu** :
- ✅ Explication du présent continu (am/is/are + -ing)
- ✅ Mention du marqueur temporel "right now"
- ✅ Exemples corrects et incorrects
- ✅ Astuce pour retenir la règle
- ✅ Source affichée : "LearnifyEnglish — original pedagogical sheet (A2)"

### Test du chatbot conversationnel

Après l'explication initiale :

1. **Taper** : `Give me another example`
   - → Devrait donner un nouvel exemple du présent continu

2. **Taper** : `Explain it more simply`
   - → Devrait reformuler avec un langage plus simple

3. **Taper** : `Give me an exercise`
   - → Devrait générer un exercice à trous

4. **Taper** : `What about with 'she'?`
   - → Devrait adapter l'exemple avec le sujet "she"

**Vérification** : Le contexte est maintenu tout au long de la conversation.

---

## 📊 Architecture du système RAG

### Flux complet

```
┌─────────────┐
│  Étudiant   │ Clique "Ask AI"
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────────┐
│ 1. CONTEXTE : Récupération de l'erreur         │
│    - Question, réponse incorrecte, correction   │
│    - Niveau CECRL, catégorie grammaticale       │
└──────┬──────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────┐
│ 2. RECHERCHE RAG : Passages pertinents          │
│    - Embedding de la requête                    │
│    - Similarité cosinus                         │
│    - Filtrage niveau + catégorie                │
│    - Top-4 passages                             │
└──────┬──────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────┐
│ 3. PROMPT : Construction enrichie               │
│    - System : rôle pédagogique                  │
│    - User : contexte + extraits documentaires   │
└──────┬──────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────┐
│ 4. GÉNÉRATION : Groq Chat API                   │
│    - Modèle : openai/gpt-oss-20b               │
│    - Température : 0.35                         │
│    - Max tokens : 900                           │
└──────┬──────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────┐
│ 5. RÉPONSE : Explication + Sources             │
│    - Affichage frontend avec références         │
└─────────────────────────────────────────────────┘
```

### Composants clés

| Composant | Fichier | Rôle |
|-----------|---------|------|
| **Service principal** | `PreevaluationAiExplanationService.java` | Orchestration Ask AI + chatbot |
| **Recherche RAG** | `RagRetrievalService.java` | Recherche documentaire vectorielle |
| **Index vectoriel** | `RagVectorIndex.java` | Similarité cosinus + filtres |
| **Prompts** | `RagPromptTemplates.java` | Construction des prompts contextuels |
| **Client Groq** | `GroqChatClient.java` | Appels API Groq |
| **Base documentaire** | `cefr_pedagogy.json` | 8+ règles grammaticales CECRL |

---

## 🎓 Points clés pour la démonstration

### 1. Problème Groq résolu

**Avant** : Erreur `llama-3.1-8b-instant does not exist`  
**Après** : Configuration correcte avec `openai/gpt-oss-20b`

**Solution** :
- ✅ Vérification de la documentation officielle Groq
- ✅ Configuration par variable d'environnement
- ✅ Valeur par défaut sécurisée

### 2. RAG complet implémenté

**Pas un simple chatbot**, mais un véritable système RAG avec :
- ✅ Base documentaire structurée (CECRL)
- ✅ Pipeline d'indexation au démarrage
- ✅ Recherche vectorielle avec filtres
- ✅ Injection documentaire dans les prompts
- ✅ Sources traçables

### 3. Prompt Engineering

**Templates structurés** avec :
- ✅ Rôle pédagogique clair
- ✅ Adaptation au niveau CECRL
- ✅ Injection des extraits pertinents
- ✅ Contexte complet de l'erreur
- ✅ Instructions de formatage

### 4. Différence mesurable

| Critère | Sans RAG | Avec RAG |
|---------|----------|----------|
| Sources | ❌ Aucune | ✅ Référencées |
| Adaptation niveau | ❌ Générique | ✅ Précise CECRL |
| Cohérence | ❌ Variable | ✅ Garantie |
| Hallucinations | ❌ Possibles | ✅ Réduites |
| Traçabilité | ❌ Aucune | ✅ Complète |

---

## 📞 Support

### Fichiers de documentation

- `README_RAG.md` (ce fichier) : Démarrage rapide
- `GUIDE_DEMONSTRATION_PROFESSEURE.md` : Guide complet pour la démo
- `IMPLEMENTATION_SUMMARY.md` : Résumé technique détaillé
- `integrated/preevaluation-service/RAG.md` : Documentation RAG technique

### Commandes utiles

```powershell
# Logs du service
Get-Content integrated\preevaluation-service\logs\spring.log -Tail 50

# Tests
cd integrated\preevaluation-service
mvn test

# Redémarrer après config
.\start-local.ps1
```

### Ressources Groq

- Console : https://console.groq.com
- Modèles disponibles : https://console.groq.com/docs/models
- Documentation API : https://console.groq.com/docs/api-reference

---

## ✨ Fonctionnalités démontrables

✅ Préévaluation du niveau d'anglais (A1-B2)  
✅ Détection des erreurs par catégorie  
✅ Assistant pédagogique intelligent (Ask AI)  
✅ Chatbot conversationnel avec contexte  
✅ Recherche documentaire automatique  
✅ Explications fondées sur sources CECRL  
✅ Adaptation au niveau de l'étudiant  
✅ Références bibliographiques affichées  
✅ Gestion d'erreurs conviviale  
✅ Fallback local si Groq indisponible  

---

**Prêt pour la démonstration !** 🎉

*Pour toute question, consulter `GUIDE_DEMONSTRATION_PROFESSEURE.md`*
