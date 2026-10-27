# 🚀 Démarrage ultra-rapide - LearnifyEnglish RAG

## 3 étapes pour tester (5 minutes)

### 1️⃣ Configuration (2 min)

```powershell
# Naviguer vers le service
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\integrated\preevaluation-service

# Créer la configuration
copy .env.example .env

# Éditer et renseigner votre clé Groq
notepad .env
```

**Dans `.env`, remplacer** :
```properties
GROQ_API_KEY=votre_cle_ici
```

**Par** (obtenir une clé gratuite sur https://console.groq.com/keys) :
```properties
GROQ_API_KEY=gsk_votre_vraie_cle_groq_ici
```

**Sauvegarder et fermer.**

---

### 2️⃣ Démarrage (2 min)

**Terminal 1 : Backend**
```powershell
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\integrated\preevaluation-service
.\start-local.ps1
```

**Attendre** : `RAG indexing completed in X.Xs` ✅

**Terminal 2 : Frontend** (nouveau terminal)
```powershell
cd c:\Users\wiemo\Desktop\merge_integ\merge_integ\frontend
npm start
```

**Attendre** : `Angular Live Development Server is listening` ✅

---

### 3️⃣ Test (1 min)

1. Ouvrir http://localhost:4200
2. Se connecter (créer un compte étudiant si nécessaire)
3. Faire la préévaluation
4. **Question test** : `I ___ a book right now.`
5. **Choisir volontairement** : `read` (au lieu de `am reading`)
6. Terminer le test
7. **Cliquer sur "Ask AI"** → L'explication RAG s'affiche avec sources ! ✨

---

## ✅ Résultat attendu

Vous devriez voir une explication structurée :

```
Why your answer is incorrect:
Your answer 'read' uses the present simple, but 'right now' 
signals the present continuous...

✓ Correct: I am reading a book right now.
✗ Incorrect: I read a book right now.

💡 Tip: When you see 'now', think 'am/is/are + -ing'!

Sources:
- LearnifyEnglish — original pedagogical sheet (A2)
```

**Tester le chatbot** :
- "Give me another example"
- "Explain it more simply"
- "What about with 'she'?"

---

## 🆘 Problèmes ?

### Erreur : "model does not exist"

→ Vérifier `.env` contient `GROQ_MODEL=openai/gpt-oss-20b`

### Erreur : "AI not configured"

→ Vérifier `GROQ_API_KEY` dans `.env` (doit commencer par `gsk_`)

### MySQL ?

```sql
CREATE DATABASE preevaluation_db;
```

Dans `.env` :
```properties
SPRING_DATASOURCE_PASSWORD=votre_mot_de_passe_mysql
```

---

## 📚 Documentation complète

- **Démarrage détaillé** : `README_RAG.md`
- **Guide démonstration** : `GUIDE_DEMONSTRATION_PROFESSEURE.md`
- **Résumé technique** : `IMPLEMENTATION_SUMMARY.md`

---

**C'est tout ! Le système RAG fonctionne.** 🎉
