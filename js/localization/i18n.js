import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import translationEN from './en/translation.json';
import translationPL from './pl/translation.json';
import translationES from './es/translation.json';

// Instead of declaring texts in individual files, we can add do the same as
/*
export const resources = {
  en: {
    translation: {
      _comment: 'src/localization/en/translation.json',
      demoScope: {
        title: 'i18next is Great!!!!!',
        description: 'Everyone understands me!',
      },
      'button.labels': {
        newdb: 'New Database',
        opendb: 'Open Database',
      },
    },
  },
  pl: {
    translation: {
      demoScope: {
        title: 'i18next jest świetne!',
        description: 'Wszyscy mnie rozumieją!',
      },
    },
  },
};
*/

export const resources = {
  en: {
    translation: translationEN,
  },
  pl: {
    translation: translationPL,
  },
  es: {
    translation: translationES,
  },
};

// We can set lng:'es' to use spanish example

// Need to use compatibilityJSON: 'v3' for android

// i18n.use(initReactI18next).init({
//   resources,
//   compatibilityJSON: 'v3',
//   lng: 'es',
//   fallbackLng: 'en',
//   interpolation: {
//     escapeValue: false,
//   },
// });

// IMPORTANT: This function needs to be called before the use of 'useTranslation'

export function initI18N(lan) {
  //return lan;
  i18n.use(initReactI18next).init({
    resources,
    compatibilityJSON: 'v3',
    lng: lan,
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
  });
}

/*
// Another way of using lanaguage selection
// See https://www.i18next.com/misc/creating-own-plugins

const languageDetector = {
  type: 'languageDetector',
  async: true,
  detect: cb => cb('pl'),
  init: () => {},
  cacheUserLanguage: () => {},
};

i18n
  .use(languageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
  });
*/
