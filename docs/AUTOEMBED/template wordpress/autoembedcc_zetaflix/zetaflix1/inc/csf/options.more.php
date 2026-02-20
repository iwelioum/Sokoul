<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('SEO'),
        'name'   => 'seo',
        'icon'   => 'fa fa-line-chart',
        'fields' => array(
            array(
                'id'    => 'seo',
                'type'  => 'switcher',
                'title' => __z('SEO Features'),
                'label' => __z('Basic SEO')
            ),
            array(
                'type'       => 'notice',
                'style'      => 'info',
                'content'    => __z('Uncheck this to disable SEO features in the theme, highly recommended if you use any other SEO plugin, that way the themes SEO features won\'t conflict with the plugin'),
                'dependency' => array('seo','==', true)
            ),
            array(
                'id'         => 'seoname',
                'type'       => 'text',
                'title'      => __z('Alternative name'),
                'dependency' => array('seo','==', true)
            ),
            array(
                'id'         => 'seokeywords',
                'type'       => 'text',
                'title'      => __z('Main keywords'),
                'subtitle'       => __z('add main keywords for site info'),
                'dependency' => array('seo','==', true)
            ),
            array(
                'id'         => 'seodescription',
                'type'       => 'textarea',
                'title'      => __z('Meta description'),
                'dependency' => array('seo','==', true)
            ),
            array(
                'type'    => 'heading',
                'content' => __z('Site verification'),
                'dependency' => array('seo','==', true)
            ),
            array(
                'id'         => 'seogooglev',
                'type'       => 'text',
                'title'      => __z('Google Search Console'),
                'after'       => '<p><a href="https://www.google.com/webmasters/verification/" target="_blank">'.__z('Settings here').'</a></p>',
                'dependency' => array('seo','==', true)
            ),
            array(
                'id'         => 'seobingv',
                'type'       => 'text',
                'title'      => __z('Bing Webmaster Tools'),
                'after'       => '<p><a href="https://www.bing.com/toolbox/webmaster/" target="_blank">'.__z('Settings here').'</a></p>',
                'dependency' => array('seo','==', true)
            ),
            array(
                'id'         => 'seoyandexv',
                'type'       => 'text',
                'title'      => __z('Yandex Webmaster Tools'),
                'after'       => '<p><a href="https://yandex.com/support/webmaster/service/rights.xml#how-to" target="_blank">'.__z('Settings here').'</a></p>',
                'dependency' => array('seo','==', true)
            )
        )
    )
);

/**
 * @since 3.4.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title' => __z('Advertising'),
        'name' => 'ads',
        'icon' => 'fa fa-usd',
        'fields' => array(
            array(
              'type'    => 'content',
              'content' => '<p><a href="'.admin_url('themes.php?page=zetaflix-ad').'"><strong>'.__z('Manage integration codes and ads').'</strong></a></p>',
            )
        )
    )
);

/**
 * @since 3.4.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title' => __z('Backup'),
        'name' => 'backup',
        'icon' => 'fa fa-database',
        'fields' => array(
            array(
              'type' => 'backup'
            )
        )
    )
);
