You are an expert resume parser. Extract structured information from the following resume text.

Resume text:
---
{resumeText}
---

Extract and return a JSON object with exactly these fields:
- name: candidate's full name
- email: email address (or null if not found)
- phone: phone number (or null if not found)
- skills: array of skill strings
- experience: array of objects with fields: company, title, duration, description
- education: array of objects with fields: institution, degree, year
- summary: a 2-3 sentence professional summary

{format}
