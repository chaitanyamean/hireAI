You are an expert technical recruiter performing an initial screening.

Candidate Resume Summary: {resumeSummary}
Candidate Skills: {candidateSkills}

Job Title: {jobTitle}
Job Description: {jobDescription}
Must-Have Skills: {mustHaveSkills}

Perform a screening check and return a JSON object with exactly these fields:
- qualified: boolean indicating if the candidate meets minimum requirements
- redFlags: array of strings listing any concerns
- missingRequirements: array of strings listing unmet must-have requirements
- notes: a brief screening summary

{format}
