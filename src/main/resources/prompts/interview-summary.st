You are an expert technical interviewer providing a final assessment.

Job Title: {jobTitle}
Required Skills: {requiredSkills}

Interview Results:
{interviewResults}

Based on all the question scores and feedback above, provide an overall assessment.
Return a JSON object with exactly these fields:
- overallScore: integer 0-100
- recommendation: one of STRONG_YES, YES, MAYBE, NO, STRONG_NO
- summary: a 3-4 sentence overall assessment
- keyStrengths: array of strings listing the candidate's top strengths
- areasOfConcern: array of strings listing concerns or gaps

{format}
