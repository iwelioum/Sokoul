<?php
/**
 * @since 1.0.0
 * @version 2.0
 */

CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Cookies Law'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
           array(
               'id'       => 'cookie_key',
               'type'     => 'text',
               'default'  => 'cookie_notice_accepted',
               'title'    => __z('Cookie Key')
           ),
           array(
               'id'       => 'cookie_text',
               'type'     => 'text',
               'default'  => __z('This Website uses cookies to make its website easier to use.'),
               'title'    => __z('Text notice')
           ),
           array(
               'id'       => 'cookie_text_readmore',
               'type'     => 'text',
               'default'  => __z('Learn more about cookies.'),
               'title'    => __z('Text Read more')
           ),
           array(
                'id'      => 'cookie_page_type',
                'type'    => 'radio',
                'title'   => __z('Details page type'),
                'default' => 'page',
                'options' => array(
                    'page'   => __z('Page link'),
                    'custom' => __z('Custom Link'),
                )
            ),
            array(
               'id'         => 'cookie_page',
               'type'       => 'select',
               'title'      => __z('Page Link'),
               'options'    => 'page',
               'dependency' => array('cookie_page_type', '==', 'page')
            ),
            array(
                'id'       => 'cookie_custom',
                'type'     => 'text',
                'title'    => __z('Custom Link'),
                'placeholder' => 'https://',
                'validate'    => 'csf_validate_url',
                'dependency'  => array('cookie_page_type', '==', 'custom')
            ),
            array(
               'id'      => 'cookie_link_target',
               'type'    => 'select',
               'title'   => __z('Page Link'),
               'default' => '_blank',
               'options'    => array(
                  '_blank'  => '_blank',
                  '_self'   => '_self',
                  '_parent' => '_parent',
                  '_top'    => '_top',
                  'none'    => __z('none')
               )
            ),
            array(
                'type' => 'content',
                'content' => '<p>'.__z('Opens the linked document in a new window or tab.').'</p>',
                'dependency' => array('cookie_link_target', '==', '_blank')
            ),
            array(
                'type' => 'content',
                'content' => '<p>'.__z('Opens the linked document in the same frame as it was clicked (this is default).').'</p>',
                'dependency' => array('cookie_link_target', '==', '_self')
            ),
            array(
                'type' => 'content',
                'content' => '<p>'.__z('Opens the linked document in the parent frame.').'</p>',
                'dependency' => array('cookie_link_target', '==', '_parent')
            ),
            array(
                'type' => 'content',
                'content' => '<p>'.__z('Opens the linked document in the full body of the window.').'</p>',
                'dependency' => array('cookie_link_target', '==', '_top')
            ),
            array(
               'id'      => 'cookie_expired',
               'type'    => 'select',
               'title'   => __z('Cookie expiry'),
               'default' => '2592000',
               'options'    => array(
                  '604800'    => __z('1 week'),
                  '2592000'   => __z('1 month'),
                  '7776000'   => __z('3 months'),
                  '15552000'  => __z('6 months'),
                  '31104000'  => __z('1 year'),
                  '311040000' => __z('infinity'),
               )
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Customize Notice')
            ),
            array(
               'id'      => 'cookie_background_color',
               'type'    => 'color',
               'title'   => __z('Background color'),
               'default' => '#2e3234'
            ),
            array(
               'id'      => 'cookie_text_color',
               'type'    => 'color',
               'title'   => __z('Text color'),
               'default' => '#ffffff'
            ),
            array(
               'id'      => 'cookie_link_color',
               'type'    => 'color',
               'title'   => __z('Link color'),
               'default' => 'rgba(255,255,255,0.7)'
            ),
            array(
               'id'      => 'cookie_link_hover_color',
               'type'    => 'color',
               'title'   => __z('Link hover color'),
               'default' => '#ffffff'
            ),
        )
    )
);
