import re
from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, field_validator, model_validator

Tag = Literal["AI", "Dev tools", "SaaS", "Software", "Cloud", "Data", "Security", "Design", "Learning"]
PHONE_OFFER = re.compile(
    r"\b(telco|telecom|cellular|prepaid|postpaid|recharge|smartphone|iphone|handset)\b"
    r"|\b(sim card|phone plan|mobile plan|data plan)\b",
    re.IGNORECASE,
)
FREE_OFFER = re.compile(r"\bfree\b|\bno[- ]cost\b|\bzero[- ]cost\b|\$0(?:\.00)?\b", re.IGNORECASE)


class FeedItem(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    title: str = Field(min_length=1, max_length=200)
    url: HttpUrl

    @field_validator("url")
    @classmethod
    def no_url_credentials(cls, value: HttpUrl) -> HttpUrl:
        if value.username or value.password:
            raise ValueError("URLs must not include credentials")
        return value


class DealInput(FeedItem):
    description: str = Field(min_length=1, max_length=4000)
    source: str = Field(min_length=1, max_length=120)
    tag: Tag
    expires: date | None = None
    offer_details: str | None = Field(default=None, max_length=10000)
    claim_steps: list[str] | None = Field(default=None, max_length=50)

    @field_validator("claim_steps", mode="before")
    @classmethod
    def validate_steps(cls, value):
        if value is None:
            return None
        if not isinstance(value, list) or any(not isinstance(step, str) or not step.strip() or len(step) > 2000 for step in value):
            raise ValueError("claim_steps must be an array of nonempty strings or null")
        return [step.strip() for step in value]

    @model_validator(mode="after")
    def software_and_free(self):
        text = f"{self.title} {self.description} {self.offer_details or ''} {' '.join(self.claim_steps or [])}"
        if PHONE_OFFER.search(text):
            raise ValueError("Phone and telco offers are not allowed")
        if not FREE_OFFER.search(text):
            raise ValueError("Describe the free offer explicitly (free, no-cost, zero-cost, or $0)")
        return self

    @field_validator("expires", mode="before")
    @classmethod
    def exact_expiry(cls, value):
        if value is not None and (not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value)):
            raise ValueError("expires must be YYYY-MM-DD or JSON null")
        return value


class PickInput(FeedItem):
    body: str = Field(min_length=1, max_length=10000)
    date: date

    @field_validator("date", mode="before")
    @classmethod
    def exact_date(cls, value):
        if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("date must be YYYY-MM-DD")
        return value
