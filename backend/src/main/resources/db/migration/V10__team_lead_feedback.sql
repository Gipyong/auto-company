-- V10: 팀 리드·팀 피드백
ALTER TABLE teams ADD COLUMN team_lead_employee_id UUID;
ALTER TABLE teams ADD CONSTRAINT fk_teams_lead_employee FOREIGN KEY (team_lead_employee_id) REFERENCES employees(id);

CREATE TABLE team_feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    subject_team_id UUID,
    author_employee_id UUID NOT NULL,
    author_team_id UUID,
    category VARCHAR(50) NOT NULL,
    score NUMERIC(2,1),
    summary TEXT,
    evidence_refs JSONB DEFAULT '[]'::jsonb,
    context_weight NUMERIC(3,2) DEFAULT 1.0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_team_feedbacks_subject ON team_feedbacks(subject_team_id);
CREATE INDEX idx_team_feedbacks_author ON team_feedbacks(author_employee_id);
CREATE INDEX idx_team_feedbacks_org ON team_feedbacks(organization_id);