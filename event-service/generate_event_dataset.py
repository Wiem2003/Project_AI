"""
=============================================================
  LearnifyEnglish - Event Knowledge Base Generator
  Script Python pour générer le dataset RAG du event-service
=============================================================

Ce script génère un dataset riche et réaliste d'événements
d'apprentissage d'anglais pour alimenter le système RAG.

Usage:
    python generate_event_dataset.py

Output:
    src/main/resources/rag/knowledge/event_knowledge.json
"""

import json
import os
import random
from datetime import datetime, timedelta

# ============================================
# DONNÉES DE BASE — Événements réalistes
# ============================================

EVENTS_DATA = [
    # ===== WORKSHOP (Ateliers pratiques) =====
    {
        "id": "evt-ws-001",
        "category": "WORKSHOP",
        "title": "Grammar Mastery Workshop: Tenses & Verb Forms",
        "description": "An intensive hands-on workshop focused on mastering English tenses. Participants will practice present simple, present continuous, past simple, present perfect, and future forms through interactive exercises, error correction activities, and real-life scenario role-plays. Each participant receives a grammar cheat sheet and workbook.",
        "targetAudience": "Beginners and low-intermediate learners (A1-B1) who struggle with choosing the correct tense in conversation and writing",
        "skills": ["grammar", "tenses", "verb forms", "sentence construction", "error correction"],
        "duration": "3 hours",
        "format": "In-person workshop with pair activities and group exercises",
        "typicalFeedback": "Finally understood the difference between present perfect and past simple! The timeline visualization was brilliant. The exercises made complex rules feel natural.",
        "keywords": ["grammar", "tenses", "verb", "present", "past", "future", "perfect", "continuous", "workshop", "A1", "A2", "B1", "beginner"],
        "rating": 4.7,
        "attendees": 45
    },
    {
        "id": "evt-ws-002",
        "category": "WORKSHOP",
        "title": "Pronunciation & Phonetics Lab",
        "description": "A practical pronunciation workshop using audio recording and playback technology. Focus on minimal pairs (ship/sheep, live/leave), word stress patterns, sentence intonation, connected speech, and the International Phonetic Alphabet (IPA). Participants record themselves and compare with native speaker models.",
        "targetAudience": "All levels who want to improve spoken English clarity and reduce accent interference",
        "skills": ["pronunciation", "phonetics", "IPA", "intonation", "word stress", "connected speech"],
        "duration": "2.5 hours",
        "format": "In-person lab with recording equipment and individual feedback",
        "typicalFeedback": "The recording exercise was eye-opening - I could hear my mistakes for the first time! My pronunciation improved noticeably after just one session.",
        "keywords": ["pronunciation", "phonetics", "speaking", "accent", "intonation", "IPA", "sounds", "oral", "stress", "rhythm"],
        "rating": 4.8,
        "attendees": 30
    },
    {
        "id": "evt-ws-003",
        "category": "WORKSHOP",
        "title": "Creative Writing in English: Short Stories & Poetry",
        "description": "Explore your creative side in English through short story writing and poetry. Learn narrative techniques, descriptive language, metaphors, similes, and how to create engaging characters. Workshop includes peer review sessions and publication in the LearnifyEnglish literary magazine.",
        "targetAudience": "Intermediate to advanced learners (B1-C1) interested in creative expression and literature",
        "skills": ["creative writing", "storytelling", "descriptive language", "literary devices", "self-expression", "peer review"],
        "duration": "3 hours",
        "format": "In-person creative workshop with writing exercises",
        "typicalFeedback": "Never thought I could write a story in English! The peer feedback was encouraging and constructive. Got published in the magazine!",
        "keywords": ["creative", "writing", "story", "poetry", "essay", "narrative", "literature", "expression", "imagination", "B1", "B2", "C1"],
        "rating": 4.6,
        "attendees": 25
    },
    {
        "id": "evt-ws-004",
        "category": "WORKSHOP",
        "title": "Conversational English Café",
        "description": "Informal conversation practice in a relaxed café setting with native speaker moderators. Topics rotate weekly: travel adventures, food and cooking, technology trends, current events, hobbies, and cultural comparisons. Includes vocabulary pre-teaching and expression of the week.",
        "targetAudience": "All levels wanting to practice speaking in a stress-free, social environment",
        "skills": ["conversation", "fluency", "listening", "social English", "vocabulary", "spontaneous speaking"],
        "duration": "1.5 hours",
        "format": "In-person casual meetup in café atmosphere with small groups",
        "typicalFeedback": "So much less stressful than a classroom! I finally started thinking in English instead of translating from my native language. Made great friends too!",
        "keywords": ["conversation", "speaking", "practice", "café", "social", "fluency", "talk", "chat", "informal", "fun"],
        "rating": 4.9,
        "attendees": 35
    },
    {
        "id": "evt-ws-005",
        "category": "WORKSHOP",
        "title": "English for Travel: Survive & Thrive Abroad",
        "description": "Essential English for travelers: airport and hotel vocabulary, ordering food, asking for directions, handling emergencies, making small talk with locals, and understanding cultural norms in English-speaking countries. Includes role-play simulations of real travel situations.",
        "targetAudience": "Beginners to intermediate learners (A1-B1) planning to travel to English-speaking countries",
        "skills": ["travel English", "practical vocabulary", "role-play", "cultural awareness", "survival English"],
        "duration": "2 hours",
        "format": "In-person workshop with immersive travel simulations",
        "typicalFeedback": "Used the airport vocabulary on my trip to London last month - it worked perfectly! The emergency phrases gave me confidence to travel alone.",
        "keywords": ["travel", "airport", "hotel", "directions", "food", "ordering", "tourist", "abroad", "vacation", "trip", "A1", "A2", "B1"],
        "rating": 4.5,
        "attendees": 50
    },

    # ===== WEBINAR (Séminaires en ligne) =====
    {
        "id": "evt-wb-001",
        "category": "WEBINAR",
        "title": "IELTS Preparation Strategies & Score Boosters",
        "description": "Comprehensive online webinar covering all four IELTS sections: Listening (note completion, matching), Reading (T/F/NG, matching headings), Writing (Task 1 data description, Task 2 essay structure), and Speaking (fluency tips, Part 2 cue cards). Includes band score analysis and time management strategies.",
        "targetAudience": "Students preparing for IELTS exam targeting band 6.0-7.5 (B1-C1 level)",
        "skills": ["IELTS preparation", "exam strategies", "time management", "academic English", "test-taking skills"],
        "duration": "2 hours",
        "format": "Online live session with Q&A and downloadable practice materials",
        "typicalFeedback": "The scoring criteria breakdown was incredibly useful. Improved my Writing score from 5.5 to 7.0 using the essay templates provided. Best IELTS prep I've found!",
        "keywords": ["IELTS", "exam", "preparation", "test", "score", "band", "listening", "reading", "writing", "speaking", "B1", "B2", "C1", "academic"],
        "rating": 4.8,
        "attendees": 120
    },
    {
        "id": "evt-wb-002",
        "category": "WEBINAR",
        "title": "English for Remote Work & Virtual Meetings",
        "description": "Master communication in English during remote work: running and participating in virtual meetings (Zoom/Teams), writing professional emails, Slack/chat etiquette, giving presentations online, handling technical difficulties in English, and collaborating with international teams across time zones.",
        "targetAudience": "Professionals working in international or remote environments (B1-B2)",
        "skills": ["professional communication", "email writing", "meeting facilitation", "remote collaboration", "digital etiquette"],
        "duration": "1.5 hours",
        "format": "Online interactive session with breakout room practice",
        "typicalFeedback": "Practical and immediately applicable! My confidence in virtual meetings with American colleagues improved dramatically. The email templates saved me hours.",
        "keywords": ["remote work", "email", "meeting", "virtual", "professional", "communication", "teams", "zoom", "collaboration", "business", "online"],
        "rating": 4.7,
        "attendees": 85
    },
    {
        "id": "evt-wb-003",
        "category": "WEBINAR",
        "title": "Vocabulary Building: Memory Techniques That Work",
        "description": "Science-backed techniques for vocabulary acquisition: spaced repetition systems (Anki), word families and roots, context clues, mnemonics, mind mapping, and using vocabulary journals effectively. Learn how to retain 50+ new words per week without burnout.",
        "targetAudience": "All levels wanting to expand their English vocabulary systematically",
        "skills": ["vocabulary acquisition", "memory techniques", "spaced repetition", "self-study strategies", "word formation"],
        "duration": "1 hour",
        "format": "Online live webinar with downloadable vocabulary toolkit",
        "typicalFeedback": "The spaced repetition technique alone was worth attending. Went from learning 10 words/week to 50+. My vocabulary doubled in two months!",
        "keywords": ["vocabulary", "words", "memory", "learning", "technique", "study", "flashcard", "spaced repetition", "Anki", "mnemonics"],
        "rating": 4.6,
        "attendees": 200
    },
    {
        "id": "evt-wb-004",
        "category": "WEBINAR",
        "title": "Advanced Listening: Understanding Native Speakers",
        "description": "Training for understanding fast-paced native English speech through podcasts, TED Talks, movies, and news broadcasts. Practice with various accents (American, British, Australian, Indian), learn to understand reduced speech forms (gonna, wanna, shoulda), and develop real-time note-taking skills.",
        "targetAudience": "Intermediate to advanced learners (B1-C1) frustrated by not understanding native speakers",
        "skills": ["listening comprehension", "accent recognition", "note-taking", "reduced speech", "real-world English"],
        "duration": "1.5 hours",
        "format": "Online session with audio/video exercises and interactive quizzes",
        "typicalFeedback": "Finally able to watch movies without subtitles! Understanding the reduced forms (gonna, wanna) was a game-changer for real conversation.",
        "keywords": ["listening", "native speaker", "podcast", "TED", "accent", "comprehension", "audio", "understanding", "American", "British"],
        "rating": 4.5,
        "attendees": 95
    },
    {
        "id": "evt-wb-005",
        "category": "WEBINAR",
        "title": "English Grammar Myths vs. Reality",
        "description": "Debunking common grammar myths and clarifying misunderstood rules. Topics include: ending sentences with prepositions, split infinitives, 'who' vs 'whom', Oxford comma debate, and when it's OK to break the rules. Presented with humor and real-world examples.",
        "targetAudience": "Intermediate to advanced learners (B1-C1) who want to understand grammar deeply",
        "skills": ["grammar awareness", "critical thinking", "language evolution", "style choices"],
        "duration": "1 hour",
        "format": "Online interactive presentation with polls and discussion",
        "typicalFeedback": "Mind-blowing! I'd been following rules that don't actually exist. Now I feel more confident about my grammar choices.",
        "keywords": ["grammar", "myths", "rules", "preposition", "comma", "style", "language", "English", "correct", "incorrect"],
        "rating": 4.4,
        "attendees": 75
    },

    # ===== CONFERENCE (Conférences) =====
    {
        "id": "evt-cf-001",
        "category": "CONFERENCE",
        "title": "AI & the Future of Language Learning",
        "description": "A full-day conference exploring how artificial intelligence is transforming language education. Keynote speakers from Google, Duolingo, and Cambridge Assessment discuss AI tutors, adaptive learning algorithms, speech recognition for pronunciation feedback, ChatGPT in language learning, and the future of human teachers in an AI world.",
        "targetAudience": "Language teachers, EdTech professionals, AI enthusiasts, and advanced learners interested in technology",
        "skills": ["AI literacy", "educational technology", "future thinking", "professional networking"],
        "duration": "Full day (6 hours)",
        "format": "In-person conference with keynotes, panels, and networking sessions",
        "typicalFeedback": "Fascinating insights into how AI is personalizing learning. The Duolingo keynote was outstanding. Made valuable professional connections.",
        "keywords": ["AI", "artificial intelligence", "technology", "future", "innovation", "education", "conference", "EdTech", "machine learning", "ChatGPT"],
        "rating": 4.9,
        "attendees": 300
    },
    {
        "id": "evt-cf-002",
        "category": "CONFERENCE",
        "title": "Global English: Accents, Dialects & World Englishes",
        "description": "Academic conference examining the diversity of English around the world. Presentations on World Englishes (Indian English, Nigerian English, Singlish), the debate around Standard English, accent discrimination, English as a Lingua Franca, and implications for teaching and testing.",
        "targetAudience": "Linguistics students, teachers, and anyone interested in the global spread of English",
        "skills": ["sociolinguistics", "cultural awareness", "accent appreciation", "academic discourse"],
        "duration": "Half day (4 hours)",
        "format": "In-person academic conference with paper presentations and Q&A",
        "typicalFeedback": "Changed my perspective on 'correct' English. Learning about World Englishes made me more accepting of my own accent. Excellent academic content.",
        "keywords": ["global English", "accent", "dialect", "world Englishes", "lingua franca", "sociolinguistics", "culture", "diversity"],
        "rating": 4.5,
        "attendees": 150
    },

    # ===== TRAINING (Formation structurée) =====
    {
        "id": "evt-tr-001",
        "category": "TRAINING",
        "title": "Professional Business Writing Masterclass",
        "description": "Comprehensive training on business writing: professional emails (tone, structure, common templates), executive reports, project proposals, meeting minutes, LinkedIn profile optimization, and formal correspondence. Includes 4 real-world writing assignments with detailed instructor feedback and a final portfolio.",
        "targetAudience": "Business professionals and students (B1-C1) who write in English for work or applications",
        "skills": ["business writing", "email etiquette", "report writing", "professional tone", "LinkedIn", "formal English"],
        "duration": "4 sessions x 2 hours (8 hours total)",
        "format": "Blended learning: online theory + in-person writing workshops",
        "typicalFeedback": "My business emails are now much more professional and concise. Got compliments from my American manager! The portfolio is great for my CV.",
        "keywords": ["business", "writing", "email", "report", "proposal", "formal", "professional", "correspondence", "B1", "B2", "C1", "LinkedIn"],
        "rating": 4.8,
        "attendees": 40
    },
    {
        "id": "evt-tr-002",
        "category": "TRAINING",
        "title": "Public Speaking & Presentation Skills in English",
        "description": "Transform your ability to present in English. Covers: presentation structure (hook, body, conclusion), slide design principles, vocal delivery and pacing, body language and eye contact, handling Q&A sessions, managing nervousness, and storytelling techniques. Each participant delivers 3 presentations with video playback analysis.",
        "targetAudience": "Students and professionals (B1-C1) who need to deliver presentations in English",
        "skills": ["public speaking", "presentation skills", "confidence building", "storytelling", "body language", "Q&A handling"],
        "duration": "3 sessions x 2 hours (6 hours total)",
        "format": "In-person training with video recording and personalized coaching",
        "typicalFeedback": "Went from terrified to confident in 3 sessions. The video playback was uncomfortable but incredibly effective. Best investment in my career!",
        "keywords": ["presentation", "speaking", "public", "slides", "confidence", "stage", "talk", "audience", "PowerPoint", "pitch"],
        "rating": 4.9,
        "attendees": 20
    },
    {
        "id": "evt-tr-003",
        "category": "TRAINING",
        "title": "Academic English for University Success",
        "description": "Essential training for university students studying in English. Covers: academic essay structure (introduction, thesis, body paragraphs, conclusion), critical analysis techniques, proper citation (APA/MLA/Harvard), paraphrasing vs. plagiarism, seminar participation skills, and defending your thesis/dissertation.",
        "targetAudience": "University students and researchers (B2-C1) studying or planning to study in English",
        "skills": ["academic writing", "research skills", "citations", "critical analysis", "seminar participation", "thesis defense"],
        "duration": "6 sessions x 2 hours (12 hours total)",
        "format": "Blended: online lectures + in-person writing clinics + peer review",
        "typicalFeedback": "My essay grades jumped from C to A after this course. The citation workshop alone saved me from accidental plagiarism. Essential for anyone at university!",
        "keywords": ["academic", "university", "research", "paper", "essay", "citation", "APA", "thesis", "student", "B2", "C1", "study"],
        "rating": 4.7,
        "attendees": 55
    },
    {
        "id": "evt-tr-004",
        "category": "TRAINING",
        "title": "TEFL Introduction: Become an English Teacher",
        "description": "Introduction to Teaching English as a Foreign Language for aspiring teachers. Covers: lesson planning methodology, classroom management strategies, error correction techniques (recasting, eliciting, explicit correction), creating engaging activities, using technology in teaching, and teaching mixed-level classes.",
        "targetAudience": "Advanced speakers (C1-C2) considering a career in English teaching",
        "skills": ["teaching methodology", "lesson planning", "classroom management", "activity design", "error correction"],
        "duration": "4 sessions x 3 hours (12 hours total)",
        "format": "In-person training with observed practice teaching and mentor feedback",
        "typicalFeedback": "This course gave me the tools and confidence to start teaching. The practice teaching with real students was invaluable. Now I teach part-time!",
        "keywords": ["teaching", "TEFL", "TESOL", "teacher", "lesson", "classroom", "pedagogy", "education", "career", "C1", "C2"],
        "rating": 4.6,
        "attendees": 15
    },

    # ===== EXAM_PREPARATION =====
    {
        "id": "evt-ep-001",
        "category": "EXAM_PREPARATION",
        "title": "TOEFL iBT Intensive Boot Camp",
        "description": "Five-day intensive TOEFL preparation covering all sections with realistic practice tests. Reading: academic passage strategies, vocabulary in context. Listening: lecture note-taking, conversation inference. Speaking: templates for all 4 tasks, timing drills. Writing: integrated task structure, independent essay outlines. Includes two full-length mock exams with score estimation.",
        "targetAudience": "University applicants needing TOEFL iBT scores of 80+ (B1-C1 level)",
        "skills": ["TOEFL strategies", "academic English", "note-taking", "integrated skills", "time management under pressure"],
        "duration": "5 sessions x 3 hours (15 hours total)",
        "format": "Intensive in-person bootcamp with daily practice tests",
        "typicalFeedback": "My score jumped from 72 to 96 after this bootcamp! The speaking templates were a lifesaver. The mock exams made the real test feel familiar.",
        "keywords": ["TOEFL", "iBT", "exam", "university", "academic", "test", "score", "study", "preparation", "mock", "practice"],
        "rating": 4.8,
        "attendees": 35
    },
    {
        "id": "evt-ep-002",
        "category": "EXAM_PREPARATION",
        "title": "Cambridge B2 First (FCE) Exam Preparation",
        "description": "Structured preparation for Cambridge B2 First exam. Covers all papers: Reading & Use of English (word formation, key word transformation, open cloze), Writing (essay, report, review formats), Listening (multiple matching, sentence completion), and Speaking (collaborative task, long turn). Exam techniques and common pitfalls.",
        "targetAudience": "Students targeting Cambridge B2 First certification (B2 level)",
        "skills": ["Cambridge exam techniques", "Use of English", "formal writing", "collocations", "exam timing"],
        "duration": "Weekend intensive (2 full days)",
        "format": "In-person bootcamp with timed practice papers",
        "typicalFeedback": "The Use of English techniques were game-changing! Passed with Grade A. The timed practice made me manage my time perfectly in the real exam.",
        "keywords": ["Cambridge", "FCE", "B2 First", "exam", "certification", "B2", "Use of English", "preparation", "certificate"],
        "rating": 4.7,
        "attendees": 30
    },
    {
        "id": "evt-ep-003",
        "category": "EXAM_PREPARATION",
        "title": "English Exam Anxiety & Stress Management",
        "description": "Specialized session addressing test anxiety for English language exams. Evidence-based techniques: progressive muscle relaxation, box breathing, cognitive reframing of negative thoughts, visualization of success, strategic time management under pressure, and when to guess vs. skip. Applicable to IELTS, TOEFL, Cambridge, and university exams.",
        "targetAudience": "Students who experience anxiety, blank-outs, or underperformance during English exams (all levels)",
        "skills": ["stress management", "test-taking confidence", "relaxation techniques", "cognitive strategies", "exam mindset"],
        "duration": "1.5 hours",
        "format": "Online interactive workshop with guided practice",
        "typicalFeedback": "I used to freeze during oral exams. The breathing technique and reframing exercises helped me stay calm and think clearly. My exam performance improved by 20%!",
        "keywords": ["anxiety", "stress", "exam", "test", "confidence", "calm", "relaxation", "mental", "pressure", "nerves", "performance"],
        "rating": 4.9,
        "attendees": 180
    },

    # ===== BUSINESS_ENGLISH =====
    {
        "id": "evt-be-001",
        "category": "BUSINESS_ENGLISH",
        "title": "Negotiation Skills & Deal-Making in English",
        "description": "Master the art of negotiation in English through realistic business simulations. Topics: negotiation vocabulary and power phrases, BATNA concept, cultural considerations in international negotiations (American vs. Asian vs. European styles), handling objections, closing deals, and post-negotiation follow-up communications.",
        "targetAudience": "Business professionals, MBA students, and entrepreneurs (B2-C1) involved in international business",
        "skills": ["negotiation", "persuasion", "business vocabulary", "intercultural competence", "deal-closing", "conflict resolution"],
        "duration": "2 sessions x 3 hours (6 hours total)",
        "format": "In-person with role-play business simulations and expert debriefing",
        "typicalFeedback": "The role-play scenarios were incredibly realistic - we negotiated a mock merger! I feel much more confident negotiating in English with international partners now.",
        "keywords": ["negotiation", "business", "deal", "persuasion", "BATNA", "professional", "MBA", "international", "contract", "sales"],
        "rating": 4.7,
        "attendees": 25
    },
    {
        "id": "evt-be-002",
        "category": "BUSINESS_ENGLISH",
        "title": "Job Interview Mastery in English",
        "description": "Complete preparation for English-language job interviews. Covers: answering behavioral questions using STAR method, discussing strengths/weaknesses authentically, salary negotiation vocabulary, body language optimization, common mistakes non-native speakers make, video interview tips, and LinkedIn networking in English. Includes 2 mock interviews with professional recruiters.",
        "targetAudience": "Job seekers targeting positions in English-speaking companies or international firms (B1-C1)",
        "skills": ["interview skills", "STAR method", "salary negotiation", "self-presentation", "professional vocabulary", "networking"],
        "duration": "2 hours intensive session",
        "format": "In-person with mock interviews and real recruiter feedback",
        "typicalFeedback": "The mock interview with a real recruiter was nerve-wracking but transformative. Got my dream job at a multinational company 2 weeks later!",
        "keywords": ["interview", "job", "career", "CV", "resume", "hiring", "STAR", "salary", "business", "employment", "recruiter", "LinkedIn"],
        "rating": 4.9,
        "attendees": 60
    },
    {
        "id": "evt-be-003",
        "category": "BUSINESS_ENGLISH",
        "title": "Effective Business Email & Professional Writing",
        "description": "Master professional email communication in English: appropriate tone and register, efficient email structure, handling complaints and difficult messages, follow-up strategies, cross-cultural email etiquette (direct vs. indirect cultures), out-of-office messages, and templates for common business scenarios.",
        "targetAudience": "Professionals who communicate in English via email daily (A2-B2)",
        "skills": ["email writing", "professional tone", "business communication", "etiquette", "complaint handling"],
        "duration": "1.5 hours",
        "format": "Online workshop with before/after email makeover exercises",
        "typicalFeedback": "My emails went from long and confusing to short and effective. My response rate from clients doubled! The complaint handling templates are gold.",
        "keywords": ["email", "business", "writing", "professional", "communication", "formal", "etiquette", "reply", "follow-up", "client", "corporate"],
        "rating": 4.6,
        "attendees": 110
    },
    {
        "id": "evt-be-004",
        "category": "BUSINESS_ENGLISH",
        "title": "English for Startups: Pitching & Fundraising",
        "description": "Learn to present your startup in English to international investors. Covers: crafting a compelling elevator pitch (30 seconds, 2 minutes, 5 minutes versions), investor deck vocabulary, financial terms in English (ROI, burn rate, runway, valuation), demo day presentation skills, and handling tough Q&A from VCs.",
        "targetAudience": "Entrepreneurs, startup founders, and aspiring business leaders (B2-C1)",
        "skills": ["pitching", "startup vocabulary", "financial English", "investor communication", "presentation under pressure"],
        "duration": "3 hours",
        "format": "In-person with pitch practice and shark-tank style feedback",
        "typicalFeedback": "Pitched my app in English at a real investor event and received funding interest! The financial vocabulary section was exactly what I needed.",
        "keywords": ["startup", "pitch", "investor", "entrepreneur", "business", "funding", "demo", "presentation", "VC", "venture capital", "fundraising"],
        "rating": 4.8,
        "attendees": 20
    },

    # ===== CULTURAL_EVENT =====
    {
        "id": "evt-ce-001",
        "category": "CULTURAL_EVENT",
        "title": "English Movie Night & Discussion Club",
        "description": "Watch a curated English-language film followed by a guided discussion in English. Before the film: vocabulary pre-teaching of key expressions. During: subtitles available in English. After: structured discussion covering cultural references, character analysis, idiomatic expressions from the film, and a short film review writing exercise.",
        "targetAudience": "All levels — relaxed, fun learning environment. Perfect for visual and auditory learners",
        "skills": ["listening comprehension", "vocabulary in context", "cultural knowledge", "discussion skills", "film analysis"],
        "duration": "3 hours (film + discussion)",
        "format": "In-person social event with snacks provided",
        "typicalFeedback": "Most enjoyable way to learn English! Picked up dozens of new expressions from the movie without even trying. The discussion after made me confident to share opinions.",
        "keywords": ["movie", "film", "cinema", "culture", "discussion", "listening", "vocabulary", "fun", "social", "entertainment", "idioms"],
        "rating": 4.8,
        "attendees": 65
    },
    {
        "id": "evt-ce-002",
        "category": "CULTURAL_EVENT",
        "title": "English Book Club: Monthly Reading Circle",
        "description": "Monthly book club reading contemporary English novels, classics, and non-fiction. Each session includes: vocabulary pre-study guide, comprehension questions, thematic discussion, author context, and a creative writing exercise inspired by the book. Recent reads: '1984', 'The Great Gatsby', 'Educated'.",
        "targetAudience": "Intermediate to advanced readers (B1-C1) who enjoy literature and want to improve reading skills",
        "skills": ["reading comprehension", "literary analysis", "vocabulary", "discussion", "critical thinking"],
        "duration": "2 hours per month",
        "format": "In-person discussion group with reading materials provided one month in advance",
        "typicalFeedback": "Reading a whole novel in English seemed impossible before, but the group support and pre-study guide made it achievable. My vocabulary has exploded!",
        "keywords": ["book", "reading", "literature", "novel", "club", "vocabulary", "discussion", "culture", "classics", "fiction"],
        "rating": 4.7,
        "attendees": 20
    },
    {
        "id": "evt-ce-003",
        "category": "CULTURAL_EVENT",
        "title": "English Debate Club: Think Fast, Speak Fluently",
        "description": "Weekly competitive debate sessions on trending topics in English. Learn parliamentary debate format, develop rapid argumentation skills, practice impromptu speaking (1-minute preparation), respectful disagreement phrases, and gain confidence expressing complex opinions in English. Topics drawn from current events, ethics, and pop culture.",
        "targetAudience": "Intermediate to advanced speakers (B1-C2) who want to sharpen fluency and critical thinking",
        "skills": ["debating", "argumentation", "critical thinking", "fluency", "impromptu speaking", "persuasion"],
        "duration": "1.5 hours weekly",
        "format": "In-person parliamentary-style debates with judge feedback",
        "typicalFeedback": "Best thing I've done for my fluency! Being forced to think and argue in English on the spot was terrifying at first but absolutely transformative.",
        "keywords": ["debate", "speaking", "argumentation", "opinion", "fluency", "critical thinking", "discussion", "confidence", "persuasion", "rhetoric"],
        "rating": 4.9,
        "attendees": 30
    },
    {
        "id": "evt-ce-004",
        "category": "CULTURAL_EVENT",
        "title": "Learn English Through Music & Song Lyrics",
        "description": "Fun cultural event analyzing English song lyrics from pop, rock, hip-hop, and folk music. Learn idioms, slang, cultural references, poetic devices, and pronunciation through popular songs. Includes: lyric gap-fill exercises, singalong session, lyric writing workshop, and music-themed vocabulary games.",
        "targetAudience": "All levels — perfect for music lovers who want to combine passion with learning",
        "skills": ["vocabulary", "pronunciation", "cultural awareness", "listening", "idiomatic expressions", "poetic language"],
        "duration": "2 hours",
        "format": "In-person fun event with live music performance",
        "typicalFeedback": "Most fun English class ever! Learning idioms through songs makes them stick in memory forever. Now I actually understand what my favorite songs mean!",
        "keywords": ["music", "songs", "lyrics", "idioms", "culture", "fun", "singing", "vocabulary", "pronunciation", "pop", "entertainment"],
        "rating": 4.8,
        "attendees": 80
    }
]


def generate_dataset():
    """Génère le dataset complet et le sauvegarde en JSON."""

    # Enrichir chaque événement avec des métadonnées
    for i, event in enumerate(EVENTS_DATA):
        # Ajouter des dates réalistes (événements passés et futurs)
        days_offset = random.randint(-180, 90)
        event_date = datetime.now() + timedelta(days=days_offset)
        event["eventDate"] = event_date.strftime("%Y-%m-%d")
        event["isUpcoming"] = days_offset > 0

        # Ajouter un prix (certains gratuits, certains payants)
        if event["category"] in ["CULTURAL_EVENT", "WEBINAR"]:
            event["price"] = random.choice([0, 0, 0, 5, 10])
        elif event["category"] == "CONFERENCE":
            event["price"] = random.choice([25, 50, 75])
        else:
            event["price"] = random.choice([10, 15, 20, 25, 30])

    dataset = {
        "metadata": {
            "name": "LearnifyEnglish Event Knowledge Base",
            "version": "1.0.0",
            "description": "Dataset of English learning events for the RAG recommendation system",
            "totalDocuments": len(EVENTS_DATA),
            "categories": list(set(e["category"] for e in EVENTS_DATA)),
            "generatedAt": datetime.now().isoformat(),
            "source": "LearnifyEnglish Platform — curated event catalog"
        },
        "documents": EVENTS_DATA
    }

    return dataset


def save_dataset(dataset, output_path):
    """Sauvegarde le dataset en JSON."""
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(dataset, f, indent=2, ensure_ascii=False)
    print(f"✅ Dataset saved to: {output_path}")
    print(f"   Total events: {dataset['metadata']['totalDocuments']}")
    print(f"   Categories: {', '.join(dataset['metadata']['categories'])}")


def print_summary(dataset):
    """Affiche un résumé du dataset."""
    docs = dataset["documents"]
    print("\n📊 Dataset Summary:")
    print("=" * 50)

    # Compter par catégorie
    categories = {}
    for doc in docs:
        cat = doc["category"]
        categories[cat] = categories.get(cat, 0) + 1

    for cat, count in sorted(categories.items()):
        print(f"  {cat}: {count} events")

    # Statistiques
    total_attendees = sum(d.get("attendees", 0) for d in docs)
    avg_rating = sum(d.get("rating", 0) for d in docs) / len(docs)
    all_skills = set()
    for d in docs:
        all_skills.update(d.get("skills", []))

    print(f"\n  Total attendees: {total_attendees}")
    print(f"  Average rating: {avg_rating:.1f}/5.0")
    print(f"  Unique skills covered: {len(all_skills)}")
    print(f"  Total keywords: {sum(len(d.get('keywords', [])) for d in docs)}")


if __name__ == "__main__":
    print("🚀 LearnifyEnglish Event Dataset Generator")
    print("=" * 50)

    # Générer le dataset
    dataset = generate_dataset()

    # Chemin de sortie
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, "src", "main", "resources", "rag", "knowledge", "event_knowledge.json")

    # Sauvegarder
    save_dataset(dataset, output_path)

    # Afficher le résumé
    print_summary(dataset)

    print("\n✅ Done! The event-service will automatically load this dataset on startup.")
    print("   Run the Spring Boot application to start the RAG system.")
